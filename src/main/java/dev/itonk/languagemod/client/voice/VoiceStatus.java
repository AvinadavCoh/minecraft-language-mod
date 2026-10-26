package dev.itonk.languagemod.client.voice;

/** What the popup shows about the mic. Kept as an interface so the HUD doesn't depend on audio code. */
public interface VoiceStatus {
	int GRAY = 0xFFAAAAAA;
	int GREEN = 0xFF55FF55;
	int YELLOW = 0xFFFFD34E;
	int RED = 0xFFFF7777;

	String statusText();

	int statusColor();

	/** Mic loudness 0..1 for the level meter. */
	float level();

	boolean showLevel();

	VoiceStatus NONE = new VoiceStatus() {
		public String statusText() { return ""; }
		public int statusColor() { return GRAY; }
		public float level() { return 0; }
		public boolean showLevel() { return false; }
	};
}
