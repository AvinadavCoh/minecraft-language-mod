package dev.itonk.languagemod.client.voice;

import dev.itonk.languagemod.LanguageMod;
import dev.itonk.languagemod.network.HudPayloads;
import dev.itonk.languagemod.voice.ModelDownloader;
import dev.itonk.languagemod.voice.SpeechTranscriber;
import dev.itonk.languagemod.voice.VoiceActivityDetector;
import dev.itonk.languagemod.voice.WavWriter;
import dev.itonk.languagemod.voice.WhisperTranscriber;
import dev.itonk.languagemod.voice.FallbackTranscriber;
import dev.itonk.languagemod.voice.VoiceText;
import dev.itonk.languagemod.voice.WhisperServerTranscriber;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Voice answers: mic -> phrase detection -> Whisper -> VoiceAnswer packet.
 *
 * Threads: the mic thread only feeds frames into the detector; whole phrases go to one
 * worker thread for Whisper; results come back to the client thread with client.execute()
 * before touching the HUD or the network. The game thread never waits on audio or Whisper.
 */
public final class VoiceInput implements VoiceStatus {
	private final VoiceSettings settings;
	private final Path modelDir;
	private final Consumer<String> onHeard; // client thread: show "you said" in the popup
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "LanguageMod-Whisper");
		t.setDaemon(true);
		return t;
	});
	private final VoiceActivityDetector vad = new VoiceActivityDetector();
	private final MicCapture mic;
	private final List<float[]> pttFrames = new ArrayList<>();
	/** Whole-challenge mic audio, kept only when saveRecordings is on. */
	private final List<float[]> challengeFrames = new ArrayList<>();
	private final Path recordingsDir;

	private volatile SpeechTranscriber transcriber;
	private volatile String state = "starting";
	private volatile int statusColor = GRAY;
	private volatile boolean thinking;
	private volatile int silentFrames;
	private volatile boolean pttHeld;

	/** Bumped on every challenge start/end so late Whisper results for an old item are dropped. */
	private volatile int generation;
	private volatile String languageCode = "ro";
	private volatile String expectedWord;
	private volatile boolean challengeOpen;
	private final java.util.concurrent.atomic.AtomicLong latestPhrase = new java.util.concurrent.atomic.AtomicLong();
	private final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();
	private volatile boolean speakingNow;
	private boolean busySent; // guarded by updateBusy()'s lock

	/** Where answers go: the server (packets) or the client-only rules. Called on the client thread. */
	public interface AnswerRoute {
		void answer(String text);

		void busy(boolean busy);
	}

	/** Default route: packets to a server that runs the mod. */
	public static final AnswerRoute TO_SERVER = new AnswerRoute() {
		@Override public void answer(String text) {
			if (ClientPlayNetworking.canSend(HudPayloads.VoiceAnswer.ID)) ClientPlayNetworking.send(new HudPayloads.VoiceAnswer(text));
		}

		@Override public void busy(boolean busy) {
			if (ClientPlayNetworking.canSend(HudPayloads.VoiceBusy.ID)) ClientPlayNetworking.send(new HudPayloads.VoiceBusy(busy));
		}
	};

	private volatile AnswerRoute route = TO_SERVER;

	public void setRoute(AnswerRoute route) {
		this.route = route;
	}

	public VoiceInput(VoiceSettings settings, Path modelDir, Consumer<String> onHeard) {
		this.settings = settings;
		this.modelDir = modelDir;
		this.onHeard = onHeard;
		this.recordingsDir = modelDir.resolveSibling("recordings");
		this.mic = new MicCapture(this::onFrame, settings.micDevice);
		mic.setGain(settings.micGain);
		StringBuilder devices = new StringBuilder();
		for (var d : MicCapture.captureDevices()) devices.append("\n  - ").append(d.getName());
		LanguageMod.LOG.info("[voice] capture devices (set \"micDevice\" in voice.json to pick one):{}", devices);
		if ("off".equalsIgnoreCase(settings.mode)) {
			setState("voice off (voice.json)", GRAY);
		} else {
			worker.execute(this::loadModel);
		}
	}

	public VoiceSettings settings() {
		return settings;
	}

	// ---- lifecycle (client thread) ---------------------------------------------------

	public void onChallengeStart(String languageCode, String expectedWord) {
		this.languageCode = languageCode;
		this.expectedWord = expectedWord;
		this.challengeOpen = true;
		generation++;
		latestPhrase.incrementAndGet(); // anything still waiting belongs to the previous word
		synchronized (this) {
			busySent = false; // the server resets its flag when a challenge starts
		}
		synchronized (vad) {
			vad.reset();
		}
		if (settings.open() && transcriber != null) openMic();
	}

	public void onChallengeEnd() {
		challengeOpen = false;
		generation++;
		latestPhrase.incrementAndGet();
		speakingNow = false;
		synchronized (this) {
			busySent = false;
		}
		if (!pttHeld) mic.stop();
		if (settings.saveRecordings) {
			float[] pcm;
			synchronized (challengeFrames) {
				pcm = join(challengeFrames);
				challengeFrames.clear();
			}
			if (pcm.length > 0) worker.execute(() -> save("challenge", pcm, null));
		}
	}

	/** Push-to-talk: called every client tick with whether the Talk key is held. */
	public void tickPushToTalk(boolean held) {
		if (!settings.pushToTalk() || transcriber == null) return;
		if (held && !pttHeld) {
			pttHeld = true;
			synchronized (pttFrames) {
				pttFrames.clear();
			}
			openMic();
		} else if (!held && pttHeld) {
			pttHeld = false;
			mic.stop();
			float[] pcm;
			synchronized (pttFrames) {
				pcm = join(pttFrames);
				pttFrames.clear();
			}
			if (pcm.length > VoiceActivityDetector.FRAME * 8) submit(pcm);
		}
	}

	/** Test hook: push recorded audio through the same detector/Whisper path as the mic. */
	public void feedTestAudio(float[] pcm) {
		for (int i = 0; i + VoiceActivityDetector.FRAME <= pcm.length; i += VoiceActivityDetector.FRAME) {
			float[] frame = new float[VoiceActivityDetector.FRAME];
			System.arraycopy(pcm, i, frame, 0, frame.length);
			onFrame(frame);
		}
		// trailing silence so the detector closes the phrase
		for (int i = 0; i < 30; i++) onFrame(new float[VoiceActivityDetector.FRAME]);
	}

	public boolean ready() {
		return transcriber != null;
	}

	// ---- audio path (mic thread) ------------------------------------------------------

	private void onFrame(float[] frame) {
		if (pttHeld) {
			synchronized (pttFrames) {
				pttFrames.add(frame);
			}
			setState("● recording", GREEN);
			return;
		}
		if (!challengeOpen) return;
		if (settings.saveRecordings) {
			synchronized (challengeFrames) {
				challengeFrames.add(frame);
			}
		}

		boolean allZero = true;
		for (float s : frame) if (s != 0f) { allZero = false; break; }
		silentFrames = allZero ? silentFrames + 1 : 0;

		float[] phrase;
		boolean speaking;
		synchronized (vad) {
			phrase = vad.accept(frame);
			speaking = vad.inSpeech();
		}
		speakingNow = speaking;
		if (phrase != null) {
			submit(phrase);
			return;
		}
		updateBusy();
		if (thinking) return;
		if (silentFrames > 100 && mic.running()) setState("mic gives pure silence: check Windows mic privacy", RED);
		else setState(speaking ? "● hearing you…" : "● listening · " + shortName(mic.deviceName()), speaking ? GREEN : GRAY);
	}

	private void submit(float[] pcm) {
		int gen = generation;
		String lang = languageCode;
		String hint = settings.hintExpectedWord ? expectedWord : null;
		// Only the newest phrase matters: if the player repeats the word while Whisper is busy,
		// older waiting phrases are skipped instead of piling up (they caused 5-24 s delays).
		long seq = latestPhrase.incrementAndGet();
		pending.incrementAndGet();
		thinking = true;
		setState("… understanding", YELLOW);
		updateBusy();
		LanguageMod.LOG.info("[voice] phrase of {} ms queued for Whisper", pcm.length * 1000 / VoiceActivityDetector.SAMPLE_RATE);
		worker.execute(() -> {
			try {
				if (seq != latestPhrase.get()) {
					LanguageMod.LOG.info("[voice] skipped an older phrase (a newer one is waiting)");
					return;
				}
				String text;
				long started = System.nanoTime();
				try {
					text = transcriber.transcribe(pcm, lang, hint);
					String engine = transcriber instanceof FallbackTranscriber f ? f.lastEngine() : "CPU";
					LanguageMod.LOG.info("[voice] Whisper ({}) took {} ms", engine, (System.nanoTime() - started) / 1_000_000);
				} catch (RuntimeException e) {
					LanguageMod.LOG.error("[voice] Whisper failed", e);
					text = "";
				}
				String heard = VoiceText.clean(text); // drops Whisper loops, caps the length for the packet
				if (heard.isEmpty() && !text.isBlank()) LanguageMod.LOG.info("[voice] ignored a Whisper loop ({} chars)", text.length());
				if (settings.saveRecordings) save("phrase", pcm, heard);
				LanguageMod.LOG.info("[voice] heard \"{}\" ({} ms of audio)", heard, pcm.length * 1000 / VoiceActivityDetector.SAMPLE_RATE);
				if (heard.isBlank()) return;
				MinecraftClient.getInstance().execute(() -> {
					if (gen != generation) return; // challenge already over
					onHeard.accept(heard);
					route.answer(heard);
				});
			} finally {
				if (pending.decrementAndGet() == 0) {
					thinking = false;
					setState("● listening", GRAY);
				}
				updateBusy(); // queued after the answer above, so the server sees the answer first
			}
		});
	}

	/**
	 * Tells the server "still talking / still understanding" so it holds the timer instead of
	 * failing a challenge whose answer is on its way. Sent only when the value changes.
	 */
	private synchronized void updateBusy() {
		boolean busy = challengeOpen && (speakingNow || pending.get() > 0);
		if (busy == busySent) return;
		busySent = busy;
		MinecraftClient.getInstance().execute(() -> route.busy(busy));
	}

	// ---- setup (worker thread) ---------------------------------------------------------

	private void loadModel() {
		Path model = modelDir.resolve(settings.model);
		try {
			if (settings.autoDownloadModel) {
				ModelDownloader.ensure(settings.modelBaseUrl + settings.model, model,
						pct -> setState("downloading voice model " + pct + "%", YELLOW));
			}
			if (!java.nio.file.Files.isRegularFile(model)) {
				setState("voice off: no model at " + model.getFileName(), RED);
				return;
			}
			setState("loading voice model…", YELLOW);
			WhisperTranscriber whisper = new WhisperTranscriber(model, settings.whisperThreads);
			whisper.setAudioCtx(settings.audioContext);
			transcriber = withGpu(model, whisper);
			setState(settings.pushToTalk() ? "hold V to talk" : "● listening", GRAY);
			LanguageMod.LOG.info("[voice] Whisper ready ({}), mode={}", settings.model, settings.mode);
			// A challenge may have opened while the model was loading.
			MinecraftClient.getInstance().execute(() -> {
				if (challengeOpen && settings.open()) openMic();
			});
		} catch (Exception e) {
			LanguageMod.LOG.error("[voice] could not set up speech-to-text", e);
			setState("voice off: " + e.getMessage(), RED);
		}
	}

	/** Wraps the CPU engine with the GPU helper when one is installed (voice.json "gpu"). */
	private SpeechTranscriber withGpu(Path model, SpeechTranscriber cpu) {
		if ("off".equalsIgnoreCase(settings.gpu)) return cpu;
		Path dir = modelDir.getParent();
		Path exe = settings.gpuServer.isBlank() ? null : Path.of(settings.gpuServer);
		if (exe == null) {
			for (Path p : List.of(dir.resolve("whisper-gpu/whisper-server.exe"), dir.resolve("whisper-gpu/Release/whisper-server.exe"))) {
				if (java.nio.file.Files.isRegularFile(p)) exe = p;
			}
		}
		if (exe == null || !java.nio.file.Files.isRegularFile(exe)) {
			LanguageMod.LOG.info("[voice] no GPU Whisper found (put the whisper.cpp CUDA build in {} or set \"gpuServer\"); using the CPU",
					dir.resolve("whisper-gpu"));
			return cpu;
		}
		try {
			WhisperServerTranscriber gpu = new WhisperServerTranscriber(exe, model, 4, settings.audioContext,
					dir.resolve("whisper-gpu.log"), dir.resolve("whisper-gpu.pid"));
			return new FallbackTranscriber(gpu, cpu);
		} catch (Exception e) {
			LanguageMod.LOG.error("[voice] could not start GPU Whisper; using the CPU", e);
			return cpu;
		}
	}

	private void openMic() {
		try {
			mic.start();
		} catch (Exception e) {
			LanguageMod.LOG.error("[voice] no microphone", e);
			setState("no microphone: " + e.getMessage(), RED);
		}
	}

	/** Debug recording: config/languagemod/recordings/KIND-yyyyMMdd-HHmmss[-heard].wav (worker thread). */
	private void save(String kind, float[] pcm, String heard) {
		String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String suffix = heard == null || heard.isBlank() ? "" : "-" + heard.replaceAll("[^\\p{L}\\p{N}]+", "_");
		if (suffix.length() > 40) suffix = suffix.substring(0, 40);
		Path file = recordingsDir.resolve(kind + "-" + stamp + suffix + ".wav");
		try {
			WavWriter.write(file, pcm);
			LanguageMod.LOG.info("[voice] saved {} ({} ms)", file.getFileName(), pcm.length / 16);
		} catch (Exception e) {
			LanguageMod.LOG.error("[voice] could not save recording", e);
		}
	}

	/** "Microphone (Realtek(R) Audio)" -> "Realtek(R) Audio" for the popup. */
	private static String shortName(String device) {
		int open = device.indexOf('(');
		return open >= 0 && device.endsWith(")") ? device.substring(open + 1, device.length() - 1) : device;
	}

	private static float[] join(List<float[]> frames) {
		float[] out = new float[frames.size() * VoiceActivityDetector.FRAME];
		int i = 0;
		for (float[] f : frames) {
			System.arraycopy(f, 0, out, i, f.length);
			i += f.length;
		}
		return out;
	}

	// ---- status for the popup ---------------------------------------------------------

	private void setState(String text, int color) {
		state = text;
		statusColor = color;
	}

	@Override
	public String statusText() {
		return state;
	}

	@Override
	public int statusColor() {
		return statusColor;
	}

	@Override
	public float level() {
		synchronized (vad) {
			return vad.level();
		}
	}

	@Override
	public boolean showLevel() {
		return mic.running();
	}
}
