package ai.alfred;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

class StartupTests {

	@Nested
	@SpringBootTest(properties = "ALFRED_DB_PASSWORD=unused-by-service-connection")
	@Import({ TestcontainersConfiguration.class, WithPostgres.ProbeListenerConfiguration.class })
	class WithPostgres {

		record RegistryProbe() {
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class ProbeListenerConfiguration {

			@Bean
			ProbeListener probeListener() {
				return new ProbeListener();
			}

		}

		static class ProbeListener {

			@ApplicationModuleListener
			void on(RegistryProbe probe) {
			}

		}

		@Autowired
		JdbcTemplate jdbc;

		@Autowired
		ApplicationEventPublisher events;

		@Autowired
		TransactionTemplate tx;

		@Test
		void runsRootMigrationIntoTheSharedModulithSchema() {
			Integer tables = jdbc.queryForObject("""
					SELECT count(*) FROM information_schema.tables
					WHERE table_schema = 'modulith' AND table_name IN ('event_publication', 'event_publication_archive')
					""", Integer.class);
			assertThat(tables).isEqualTo(2);
		}

		@Test
		void eventPublicationRegistryWritesToTheModulithSchema() {
			tx.executeWithoutResult(status -> events.publishEvent(new RegistryProbe()));

			Integer rows = jdbc.queryForObject(
					"SELECT count(*) FROM modulith.event_publication WHERE event_type = ?", Integer.class,
					RegistryProbe.class.getName());
			assertThat(rows).isEqualTo(1);
		}

	}

	@Nested
	@ExtendWith(OutputCaptureExtension.class)
	class WithoutPostgres {

		@Test
		void refusesToStartAndSaysPostgresIsRequired(CapturedOutput output) {
			SpringApplicationBuilder app = new SpringApplicationBuilder(AlfredApplication.class)
					.web(WebApplicationType.NONE);

			// Command-line args: they win over application.yml and env, so a local Postgres on 5432 can't interfere.
			assertThatException().isThrownBy(() -> {
				try (var context = app.run("--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/alfred",
						"--ALFRED_DB_PASSWORD=unused")) {
					// unreachable when startup fails as expected
				}
			});
			assertThat(output).contains("alfred requires PostgreSQL and will not start without it");
		}

	}

	@Test
	void composeAndTestcontainersUseTheSamePinnedImage() throws IOException {
		assertThat(Files.readAllLines(Path.of("compose.yaml")))
				.anyMatch(line -> line.strip().equals("image: " + TestcontainersConfiguration.POSTGRES_IMAGE));
	}

}
