package ai.alfred.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Publishes how to reach the daemon: {@code {"port": N, "token": "..."}} in {@code <runtime dir>/daemon.json}, readable
 * only by the user (0600 in a 0700 directory on POSIX; the user profile's ACL on Windows). Written once the HTTP port
 * is known, removed on shutdown.
 */
@Component
class RuntimeFile implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(RuntimeFile.class);

	private static final Set<PosixFilePermission> OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------");

	private static final Set<PosixFilePermission> OWNER_ONLY_DIR = PosixFilePermissions.fromString("rwx------");

	private final Path file;

	private final DaemonToken token;

	RuntimeFile(CliProperties properties, DaemonToken token) {
		this.file = properties.resolveRuntimeDir(System.getenv(), System.getProperty("user.home"))
			.resolve(CliProperties.RUNTIME_FILE);
		this.token = token;
	}

	@EventListener
	void onWebServerReady(WebServerInitializedEvent event) {
		// Only the main server; a separate management server would have its own namespace.
		if (event.getApplicationContext().getServerNamespace() == null) {
			write(event.getWebServer().getPort());
		}
	}

	Path path() {
		return file;
	}

	void write(int port) {
		String json = "{\"port\": %d, \"token\": \"%s\"}%n".formatted(port, token.value());
		try {
			Path dir = file.getParent();
			boolean posix = dir.getFileSystem().supportedFileAttributeViews().contains("posix");
			createDirectory(dir, posix);
			Path temp = dir.resolve(CliProperties.RUNTIME_FILE + ".tmp");
			Files.deleteIfExists(temp);
			if (posix) {
				Files.createFile(temp, PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE));
			}
			else {
				Files.createFile(temp);
			}
			Files.writeString(temp, json, StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			log.info("Daemon reachable at 127.0.0.1:{}; connection details in {}", port, file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not write the runtime file " + file, ex);
		}
	}

	private static void createDirectory(Path dir, boolean posix) throws IOException {
		if (Files.isDirectory(dir)) {
			// Never chmod a directory we did not create (it could be $HOME or /tmp); refuse a non-private one instead.
			if (posix && !Files.getPosixFilePermissions(dir).equals(OWNER_ONLY_DIR)) {
				throw new IOException("Runtime directory " + dir + " must be private (rwx------), but is "
						+ PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
			}
			return;
		}
		try {
			if (posix) {
				Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIR));
			}
			else {
				Files.createDirectories(dir);
			}
		}
		catch (FileAlreadyExistsException ex) {
			// created concurrently; permissions are checked on the next start
		}
	}

	/** Removes the file only if it is still ours: a second daemon may have replaced it since. */
	@Override
	public void destroy() throws IOException {
		if (Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).contains(token.value())) {
			Files.delete(file);
		}
	}

}
