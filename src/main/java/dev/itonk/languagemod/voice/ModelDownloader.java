package dev.itonk.languagemod.voice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.IntConsumer;

/** Fetches the Whisper model once into config/languagemod/whisper/, via a .part file. */
public final class ModelDownloader {
	private ModelDownloader() {}

	/** Downloads {@code url} to {@code target} unless it already exists. Reports 0..100 progress. */
	public static void ensure(String url, Path target, IntConsumer progress) throws IOException, InterruptedException {
		if (Files.isRegularFile(target) && Files.size(target) > 0) return;
		Files.createDirectories(target.getParent());
		Path part = target.resolveSibling(target.getFileName() + ".part");

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + url);

		long total = response.headers().firstValueAsLong("content-length").orElse(-1);
		try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
			byte[] buf = new byte[1 << 16];
			long done = 0;
			int lastPct = -1;
			for (int n; (n = in.read(buf)) > 0; ) {
				out.write(buf, 0, n);
				done += n;
				int pct = total > 0 ? (int) (done * 100 / total) : 0;
				if (pct != lastPct) progress.accept(lastPct = pct);
			}
			if (total > 0 && done != total) throw new IOException("Download cut short: " + done + "/" + total + " bytes");
		}
		Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
