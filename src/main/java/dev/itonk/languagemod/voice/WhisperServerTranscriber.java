package dev.itonk.languagemod.voice;

import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * whisper.cpp on the GPU: runs the official CUDA build's whisper-server.exe as a helper process
 * on 127.0.0.1 (nothing leaves the PC) and sends each phrase to it over HTTP.
 *
 * The process starts in the background; {@link #ready()} turns true once it answers. The first
 * start on a new PC can take ~2 minutes while CUDA compiles its kernels (cached after that).
 * Use from one worker thread.
 */
public final class WhisperServerTranscriber implements SpeechTranscriber {
	private static final Logger LOG = LoggerFactory.getLogger("languagemod");
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	private final Process process;
	private final URI inference;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	private final int audioCtx;
	private volatile boolean ready;

	/**
	 * @param pidFile remembers the helper's process id, so one left behind by a crashed game is
	 *                stopped on the next start instead of holding GPU memory forever
	 */
	public WhisperServerTranscriber(Path exe, Path model, int threads, int audioCtx, Path logFile, Path pidFile) throws IOException {
		this.audioCtx = audioCtx;
		stopLeftover(pidFile);
		int port = freePort();
		List<String> cmd = new ArrayList<>(List.of(exe.toString(), "-m", model.toString(),
				"--host", "127.0.0.1", "--port", Integer.toString(port), "-t", Integer.toString(threads),
				"-nt", "-nf"));
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(exe.getParent().toFile())
				.redirectErrorStream(true).redirectOutput(logFile.toFile());
		this.process = pb.start();
		Files.writeString(pidFile, Long.toString(process.pid()));
		this.inference = URI.create("http://127.0.0.1:" + port + "/inference");
		Runtime.getRuntime().addShutdownHook(new Thread(process::destroy, "LanguageMod-WhisperGPU-stop"));
		LOG.info("[voice] GPU Whisper starting: {} (pid {}, port {})", exe, process.pid(), port);

		Thread waiter = new Thread(() -> waitUntilReady(port), "LanguageMod-WhisperGPU-wait");
		waiter.setDaemon(true);
		waiter.start();
	}

	public boolean ready() {
		return ready && process.isAlive();
	}

	public boolean alive() {
		return process.isAlive();
	}

	private void waitUntilReady(int port) {
		long started = System.nanoTime();
		HttpRequest ping = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
				.timeout(Duration.ofSeconds(2)).GET().build();
		while (process.isAlive()) {
			try {
				http.send(ping, HttpResponse.BodyHandlers.discarding());
				// The first real job loads the GPU kernels (~14 s measured in game). Do it now on silence,
				// so the player's first answer doesn't wait; until then the CPU answers.
				try {
					request(new float[WhisperTranscriber.MIN_SAMPLES], "ro", null);
				} catch (RuntimeException e) {
					LOG.warn("[voice] GPU Whisper warm-up failed: {}", e.getMessage());
				}
				ready = true;
				LOG.info("[voice] GPU Whisper ready after {} s (warmed up)", (System.nanoTime() - started) / 1_000_000_000);
				return;
			} catch (IOException e) {
				try { Thread.sleep(500); } catch (InterruptedException ie) { return; }
			} catch (InterruptedException e) {
				return;
			}
		}
		LOG.warn("[voice] GPU Whisper exited with code {} before it was ready (see whisper-gpu.log); using the CPU",
				process.exitValue());
	}

	@Override
	public String transcribe(float[] pcm, String languageCode, String hint) {
		if (!ready()) throw new IllegalStateException("GPU Whisper is not running");
		return request(pcm, languageCode, hint);
	}

	private String request(float[] pcm, String languageCode, String hint) {
		if (pcm.length < WhisperTranscriber.MIN_SAMPLES) pcm = Arrays.copyOf(pcm, WhisperTranscriber.MIN_SAMPLES);
		int ctx = audioCtx <= 0 ? 0 : Math.min(1500, Math.max(audioCtx, pcm.length / 320 + 64));

		Multipart body = new Multipart();
		body.file("file", "phrase.wav", WavWriter.bytes(pcm));
		body.field("language", languageCode);
		if (hint != null && !hint.isBlank()) body.field("prompt", hint);
		body.field("response_format", "json");
		body.field("temperature", "0");
		body.field("temperature_inc", "0");
		body.field("audio_ctx", Integer.toString(ctx));
		body.field("suppress_non_speech", "true");

		HttpRequest request = HttpRequest.newBuilder(inference).timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "multipart/form-data; boundary=" + body.boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.finish())).build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) throw new IllegalStateException("GPU Whisper HTTP " + response.statusCode() + ": " + response.body());
			var json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (json.has("error")) throw new IllegalStateException("GPU Whisper: " + json.get("error"));
			return json.get("text").getAsString().trim();
		} catch (IOException e) {
			throw new IllegalStateException("GPU Whisper request failed", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted", e);
		}
	}

	@Override
	public void close() {
		process.destroy();
	}

	private static int freePort() throws IOException {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}

	private static void stopLeftover(Path pidFile) {
		try {
			if (!Files.isRegularFile(pidFile)) return;
			long pid = Long.parseLong(Files.readString(pidFile).trim());
			ProcessHandle.of(pid).ifPresent(p -> {
				// Only our helper: never stop an unrelated process that reused the id.
				if (p.info().command().map(c -> c.toLowerCase().endsWith("whisper-server.exe")).orElse(false)) {
					LOG.info("[voice] stopping a GPU Whisper left over from last time (pid {})", pid);
					p.destroy();
				}
			});
		} catch (IOException | RuntimeException e) {
			LOG.debug("[voice] no leftover GPU Whisper", e);
		}
	}

	/** Minimal multipart/form-data body (UTF-8 fields, so the Romanian hint keeps its letters). */
	private static final class Multipart {
		final String boundary = "----languagemod" + UUID.randomUUID().toString().replace("-", "");
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		void field(String name, String value) {
			write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
		}

		void file(String name, String filename, byte[] data) {
			write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename
					+ "\"\r\nContent-Type: audio/wav\r\n\r\n");
			out.writeBytes(data);
			write("\r\n");
		}

		byte[] finish() {
			write("--" + boundary + "--\r\n");
			return out.toByteArray();
		}

		private void write(String s) {
			out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
		}
	}
}
