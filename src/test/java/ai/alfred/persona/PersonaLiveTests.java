package ai.alfred.persona;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ai.alfred.TestcontainersConfiguration;
import ai.alfred.agent.Agent;
import ai.alfred.agent.AgentOutput;
import ai.alfred.agent.AgentOutputListener;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Alfred's personality against the real model ({@code ./gradlew liveTest}, needs {@code ANTHROPIC_API_KEY}). The
 * assertions catch only the hard rules; each reply is printed and judged by hand with
 * {@code src/test/resources/persona/live-checklist.md}.
 */
@Tag("live")
@SpringBootTest(properties = "ALFRED_DB_PASSWORD=unused-by-service-connection")
@Import({ TestcontainersConfiguration.class, PersonaLiveTests.Capture.class })
class PersonaLiveTests {

	@TestConfiguration(proxyBeanMethods = false)
	static class Capture {

		@Bean
		LinkedBlockingQueue<AgentOutput> outputs() {
			return new LinkedBlockingQueue<>();
		}

		@Bean
		AgentOutputListener capturingListener(LinkedBlockingQueue<AgentOutput> outputs) {
			return outputs::add;
		}

	}

	@Autowired
	Agent agent;

	@Autowired
	LinkedBlockingQueue<AgentOutput> outputs;

	@ParameterizedTest
	@ValueSource(strings = { "Merhaba alfred", "Docker yine kafayı yedi.", "Server neden yavaş?", "Alfred, hallet.",
			"Bugün hava nasıl olacak sence?" })
	void answersInCharacter(String message) throws InterruptedException {
		UUID taskId = agent.submit(message);
		AgentOutput output = outputs.poll(60, TimeUnit.SECONDS);

		assertThat(output).isInstanceOf(AgentOutput.Reply.class);
		assertThat(output.taskId()).isEqualTo(taskId);
		System.out.printf("%n[persona-live] Emre: %s%n[persona-live] Alfred: %s%n", message, output.text());
		assertThat(output.text()).containsIgnoringCase("efendim").doesNotContain("Sayın efendim");
		// A short chat answer: at most three sentences (FR-1); a rough count is enough to catch rambling.
		assertThat(output.text().split("[.!?]+\\s").length).isLessThanOrEqualTo(3);
	}

}
