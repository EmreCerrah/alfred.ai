package ai.alfred.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.anthropic.models.beta.messages.BetaContentBlockParam;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Smoke test against the real API with {@code claude-haiku-5-5}: run with {@code ./gradlew liveTest} and
 * {@code ANTHROPIC_API_KEY} set. Costs well under one cent per run.
 */
@Tag("live")
class ClaudeSmokeLiveTests {

	static final ToolDefinition CONTAINER_STATUS = new ToolDefinition("container_status",
			"Returns the current state of a Docker container on this machine, for example running or exited with code 137.",
			"""
					{"type": "object",
					 "properties": {"name": {"type": "string", "description": "Container name, e.g. redis"}},
					 "required": ["name"]}
					""");

	static LlmPort port;

	@BeforeAll
	static void requireApiKey() {
		String apiKey = System.getenv("ANTHROPIC_API_KEY");
		assertThat(apiKey).as("live tests need ANTHROPIC_API_KEY").isNotBlank();
		port = new ClaudeLlmAdapter(new LlmProperties("claude-haiku-5-5", "medium", 4096, apiKey, null, 2,
				Duration.ofSeconds(60), 20000));
	}

	@Test
	void callsAToolThenAnswersFromItsResultAndReadsTheCachedPrefix() {
		UUID taskId = UUID.randomUUID();
		List<LlmMessage> history = new ArrayList<>(
				List.of(new LlmMessage.User("Redis container'inin durumu ne? Araci kullanarak kontrol et.")));

		LlmResponse first = port.complete(new LlmRequest(taskId, SYSTEM, List.of(CONTAINER_STATUS), history));

		assertThat(first.stopReason()).isEqualTo(StopReason.TOOL_USE);
		ToolCall call = first.message().toolCalls().getFirst();
		assertThat(call.name()).isEqualTo("container_status");
		// Haiku 5.5 thinks by default; this is the block whose order the replay below must keep.
		assertThat(first.message().content().blocks().getFirst()).isInstanceOfSatisfying(BetaContentBlockParam.class,
				block -> assertThat(block.isThinking()).isTrue());

		history.add(first.message());
		history.add(new LlmMessage.ToolResults(List.of(new ToolResult(call.id(), "redis: exited (137) 12 minutes ago",
				false))));
		// The second round replays the first turn's thinking blocks; block_binding=error turns any edit into a 400.
		LlmResponse second = port.complete(new LlmRequest(taskId, SYSTEM, List.of(CONTAINER_STATUS), history));

		assertThat(second.stopReason()).isEqualTo(StopReason.END_TURN);
		assertThat(second.message().text()).isNotBlank();
		assertThat(second.usage().cacheReadTokens()).as("system + tools prefix read from cache").isPositive();
	}

	/** Above Haiku 5.5's 512-token minimum, so the fixed prefix is actually cached. */
	static final String SYSTEM = """
			You are Alfred, a calm senior technical assistant living on Emre's Linux workstation. You speak Turkish and \
			address the user as "efendim". You answer briefly and only from evidence you collected with your tools.
			""" + """
			Rules for using tools:
			- Prefer reading state before drawing conclusions. When the user asks about a container, check its status \
			with the container_status tool before you say anything about it.
			- Never assume the result of a tool call. If a tool fails, say so plainly and do not guess.
			- Separate a hypothesis from a verified result. Exit code 137 usually means the process was killed by the \
			operating system, often because it ran out of memory, but that is a hypothesis until the logs confirm it.
			- Keep answers to at most three sentences unless the user asks for detail.
			- Do not suggest destructive actions. If a fix is possible, offer it and wait for the user's decision.
			""".repeat(6);

}
