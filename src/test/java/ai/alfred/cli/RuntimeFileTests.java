package ai.alfred.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeFileTests {

	@TempDir
	Path temp;

	@Test
	void createsAMissingDirectoryPrivatelyAndTheFileOwnerOnly() throws Exception {
		assumePosix();
		Path dir = temp.resolve("missing").resolve("alfred");
		RuntimeFile file = new RuntimeFile(new CliProperties(dir.toString()), new DaemonToken());

		file.write(4711);

		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir))).isEqualTo("rwx------");
		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file.path()))).isEqualTo("rw-------");
	}

	@Test
	void refusesAnExistingDirectoryOthersCanReadInsteadOfChangingIt() throws Exception {
		assumePosix();
		Path shared = Files.createDirectory(temp.resolve("shared"),
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
		RuntimeFile file = new RuntimeFile(new CliProperties(shared.toString()), new DaemonToken());

		assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> file.write(4711))
			.withMessageContaining("Could not write");
		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(shared))).isEqualTo("rwxr-xr-x");
		assertThat(file.path()).doesNotExist();
	}

	@Test
	void writesPortAndTokenAndRemovesOnlyItsOwnFileOnShutdown() throws Exception {
		DaemonToken token = new DaemonToken();
		RuntimeFile file = new RuntimeFile(new CliProperties(temp.resolve("alfred").toString()), token);

		file.write(4711);
		assertThat(Files.readString(file.path())).contains("4711").contains(token.value());

		file.destroy();
		assertThat(file.path()).doesNotExist();
	}

	@Test
	void leavesAFileThatASecondDaemonReplacedAlone() throws Exception {
		Path dir = temp.resolve("alfred");
		RuntimeFile first = new RuntimeFile(new CliProperties(dir.toString()), new DaemonToken());
		RuntimeFile second = new RuntimeFile(new CliProperties(dir.toString()), new DaemonToken());
		first.write(4711);
		second.write(4712);

		first.destroy();

		assertThat(Files.readString(second.path())).contains("4712");
	}

	private void assumePosix() {
		assumeThat(temp.getFileSystem().supportedFileAttributeViews()).as("POSIX permissions").contains("posix");
	}

}
