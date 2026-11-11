package ai.alfred.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ai.alfred.TestcontainersConfiguration;
import ai.alfred.llm.LlmMessage;
import ai.alfred.memory.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Two real Turns on one Conversation ({@code ./gradlew liveTest}, needs {@code ANTHROPIC_API_KEY}). The second call
 * replays the first assistant turn with its thinking blocks under {@code prefix_mismatch_behavior=error}: any edit to
 * the history would come back as a 400 ({@code llm.rejected}) instead of a reply.
 */
@Tag("live")
@SpringBootTest(properties = "ALFRED_DB_PASSWORD=unused-by-service-connection")
@Import({ TestcontainersConfiguration.class, ConversationLiveTests.Capture.class })
class ConversationLiveTests {

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

	@Autowired
	Conversation<LlmMessage> conversation;

	@BeforeEach
	void newConversation() {
		conversation.reset();
	}

	@Test
	void resolvesAReferenceToThePreviousExchange() throws Exception {
		AgentOutput first = ask("Az önce Redis container'ının adını kontrol ettim: tam adı kirmizi-kedi-42. "
				+ "Bunu aklında tut, şimdilik yalnızca tamam de.");
		assertThat(first).isInstanceOf(AgentOutput.Reply.class);

		AgentOutput second = ask("Onun tam adı neydi? Yalnızca adı yaz.");

		// A Failure here (llm.rejected) means the replayed history was not byte-identical.
		assertThat(second).isInstanceOf(AgentOutput.Reply.class);
		assertThat(second.text()).contains("kirmizi-kedi-42");
		assertThat(conversation.messages()).hasSize(4);
		assertThat(conversation.messages().get(1)).isInstanceOf(LlmMessage.Assistant.class);
	}

	private AgentOutput ask(String message) throws InterruptedException {
		UUID taskId = agent.submit(message);
		AgentOutput output = outputs.poll(60, TimeUnit.SECONDS);
		assertThat(output).as("agent output").isNotNull();
		assertThat(output.taskId()).isEqualTo(taskId);
		System.out.printf("%n[conversation-live] Emre: %s%n[conversation-live] Alfred: %s%n", message, output.text());
		// The slot is freed before the output is emitted, so the next message is accepted at once.
		return output;
	}

}
