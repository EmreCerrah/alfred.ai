package ai.alfred.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where the daemon listens and the token it expects, read from the runtime file it writes on every start.
 */
record DaemonEndpoint(int port, String token) {

	static final String RUNTIME_FILE = "daemon.json";

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	@Override
	public String toString() {
		return "DaemonEndpoint[port=" + port + ", token=***]";
	}

	static DaemonEndpoint load(Path file) throws DaemonNotRunning {
		try {
			JsonNode node = JSON.readTree(Files.readString(file));
			return new DaemonEndpoint(node.get("port").asInt(), node.get("token").asString());
		}
		catch (NoSuchFileException ex) {
			throw new DaemonNotRunning("alfred daemon is not running (no " + file + ")");
		}
		catch (IOException | RuntimeException ex) {
			throw new DaemonNotRunning("Cannot read " + file + ": " + ex.getMessage());
		}
	}

	/**
	 * Must match the daemon's {@code CliProperties.defaultRuntimeDir}: {@code ALFRED_RUNTIME_DIR}, else
	 * {@code $XDG_RUNTIME_DIR/alfred}, else {@code %LOCALAPPDATA%\alfred}, else {@code ~/.local/state/alfred}.
	 */
	static Path runtimeFile(Map<String, String> env, String userHome) {
		return runtimeDir(env, userHome).resolve(RUNTIME_FILE);
	}

	static Path runtimeDir(Map<String, String> env, String userHome) {
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
