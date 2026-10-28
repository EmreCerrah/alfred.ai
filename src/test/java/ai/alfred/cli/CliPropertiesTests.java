package ai.alfred.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Same cases as the thin client's {@code DaemonClientTests.resolvesTheSameRuntimeDirectoryAsTheDaemon}: both sides
 * must find the same runtime file.
 */
class CliPropertiesTests {

	@Test
	void resolvesTheRuntimeDirectoryLikeTheThinClient() {
		assertThat(CliProperties.defaultRuntimeDir(Map.of("ALFRED_RUNTIME_DIR", "/x", "XDG_RUNTIME_DIR", "/run/user/1000"),
				"/home/e"))
			.isEqualTo(Path.of("/x"));
		assertThat(CliProperties.defaultRuntimeDir(Map.of("XDG_RUNTIME_DIR", "/run/user/1000"), "/home/e"))
			.isEqualTo(Path.of("/run/user/1000", "alfred"));
		assertThat(CliProperties.defaultRuntimeDir(Map.of("LOCALAPPDATA", "C:/Users/e/AppData/Local"), "/home/e"))
			.isEqualTo(Path.of("C:/Users/e/AppData/Local", "alfred"));
		assertThat(CliProperties.defaultRuntimeDir(Map.of(), "/home/e"))
			.isEqualTo(Path.of("/home/e", ".local", "state", "alfred"));
	}

	@Test
	void anExplicitRuntimeDirWins() {
		assertThat(new CliProperties("/configured").resolveRuntimeDir(Map.of("ALFRED_RUNTIME_DIR", "/x"), "/home/e"))
			.isEqualTo(Path.of("/configured"));
	}

	@Test
	void recognisesOnlyLoopbackHosts() {
		assertThat(ChannelSecurityFilter.isLoopback("127.0.0.1:4711")).isTrue();
		assertThat(ChannelSecurityFilter.isLoopback("localhost:4711")).isTrue();
		assertThat(ChannelSecurityFilter.isLoopback("[::1]:4711")).isTrue();
		assertThat(ChannelSecurityFilter.isLoopback("LOCALHOST")).isTrue();
		assertThat(ChannelSecurityFilter.isLoopback("evil.example:4711")).isFalse();
		assertThat(ChannelSecurityFilter.isLoopback("localhost.evil.example")).isFalse();
		assertThat(ChannelSecurityFilter.isLoopback(null)).isFalse();
	}

}
