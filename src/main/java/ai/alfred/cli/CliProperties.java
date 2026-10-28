package ai.alfred.cli;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code alfred.cli.*}. {@code runtimeDir} empty means the platform default (see {@link #resolveRuntimeDir}); the thin
 * client resolves the same directory the same way.
 */
@ConfigurationProperties("alfred.cli")
record CliProperties(String runtimeDir) {

	static final String RUNTIME_FILE = "daemon.json";

	/**
	 * {@code ALFRED_RUNTIME_DIR}, else {@code $XDG_RUNTIME_DIR/alfred} (Linux, a per-user tmpfs), else
	 * {@code %LOCALAPPDATA%\alfred} (Windows), else {@code ~/.local/state/alfred}.
	 */
	Path resolveRuntimeDir(Map<String, String> env, String userHome) {
		if (runtimeDir != null && !runtimeDir.isBlank()) {
			return Path.of(runtimeDir);
		}
		return defaultRuntimeDir(env, userHome);
	}

	static Path defaultRuntimeDir(Map<String, String> env, String userHome) {
		String explicit = env.get("ALFRED_RUNTIME_DIR");
		if (explicit != null && !explicit.isBlank()) {
			return Path.of(explicit);
		}
		String xdg = env.get("XDG_RUNTIME_DIR");
		if (xdg != null && !xdg.isBlank()) {
			return Path.of(xdg, "alfred");
		}
		String localAppData = env.get("LOCALAPPDATA");
		if (localAppData != null && !localAppData.isBlank()) {
			return Path.of(localAppData, "alfred");
		}
		return Path.of(userHome, ".local", "state", "alfred");
	}

}
