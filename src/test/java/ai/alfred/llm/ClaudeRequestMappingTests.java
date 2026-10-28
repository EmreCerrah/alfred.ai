package ai.alfred.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClaudeRequestMappingTests {

	static final LlmProperties PROPERTIES = new LlmProperties("claude-haiku-5-5", "medium", 16000, "unused", null, 0,
			Duration.ofSeconds(5), 40);

	static final ToolDefinition CONTAINER_STATUS = new ToolDefinition("container_status",
			"Returns the state of a Docker container.", """
					{"type": "object",
					 "properties": {"name": {"type": "string"}},
					 "required": ["name"]}
					""");

	/** A Haiku 5.5 tool-use turn as the API returns it: thinking (text omitted) first, then text, then the call. */
	static final String TOOL_USE_RESPONSE = """
			{"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-haiku-5-5",
			 "content": [
			   {"type": "thinking", "thinking": "", "signature": "sig-1"},
			   {"type": "text", "text": "Bakiyorum, efendim."},
			   {"type": "tool_use", "id": "toolu_1", "name": "container_status", "input": {"name": "redis"}}
			 ],
			 "stop_reason": "tool_use", "stop_sequence": null,
			 "usage": {"input_tokens": 900, "output_tokens": 40, "cache_read_input_tokens": 0,
			           "cache_creation_input_tokens": 850}}
			""";

	final ClaudeRequestMapper mapper = new ClaudeRequestMapper(PROPERTIES);

	final UUID taskId = UUID.randomUUID();

	@Test
	void sendsNoSamplingParametersAndEndsWithAUserTurn() {
		JsonNode body = body(request(List.of(new LlmMessage.User("Redis neden durdu?"))));

		assertThat(body.has("temperature")).isFalse();
		assertThat(body.has("top_p")).isFalse();
		assertThat(body.has("top_k")).isFalse();
		assertThat(body.at("/messages").get(body.at("/messages").size() - 1).at("/role").asText()).isEqualTo("user");
		assertThat(body.at("/model").asText()).isEqualTo("claude-haiku-5-5");
		assertThat(body.at("/thinking/type").asText()).isEqualTo("adaptive");
		assertThat(body.at("/thinking/block_binding/prefix_mismatch_behavior").asText()).isEqualTo("error");
		assertThat(body.at("/output_config/effort").asText()).isEqualTo("medium");
		assertThat(body.at("/system/0/cache_control/type").asText()).isEqualTo("ephemeral");
		assertThat(body.at("/cache_control/type").asText()).as("automatic history breakpoint").isEqualTo("ephemeral");
	}

	@Test
	void passesToolSchemasThroughUnchanged() {
		JsonNode tool = body(request(List.of(new LlmMessage.User("?")))).at("/tools/0");

		assertThat(tool.at("/name").asText()).isEqualTo("container_status");
		assertThat(tool.at("/input_schema/type").asText()).isEqualTo("object");
		assertThat(tool.at("/input_schema/properties/name/type").asText()).isEqualTo("string");
		assertThat(tool.at("/input_schema/required/0").asText()).isEqualTo("name");
	}

	@Test
	void rejectsToolSchemasThatAreNotObjects() {
		for (String schema : List.of("{\"type\": \"array\"}", "null")) {
			ToolDefinition tool = new ToolDefinition("broken", "?", schema);

			assertThatIllegalArgumentException().isThrownBy(() -> mapper
				.toParams(new LlmRequest(taskId, "You are Alfred.", List.of(tool), List.of(new LlmMessage.User("?")))));
		}
	}

	@Test
	void rejectsAnAssistantPrefill() {
		LlmMessage.Assistant assistant = mapper.toResponse(message(TOOL_USE_RESPONSE)).message();

		assertThatIllegalArgumentException()
			.isThrownBy(() -> request(List.of(new LlmMessage.User("?"), assistant)));
	}

	@Test
	void readsToolCallsStopReasonAndUsage() {
		LlmResponse response = mapper.toResponse(message(TOOL_USE_RESPONSE));

		assertThat(response.stopReason()).isEqualTo(StopReason.TOOL_USE);
		assertThat(response.message().text()).isEqualTo("Bakiyorum, efendim.");
		assertThat(response.message().toolCalls()).singleElement().satisfies(call -> {
			assertThat(call.id()).isEqualTo("toolu_1");
			assertThat(call.name()).isEqualTo("container_status");
			assertThat(json(call.argumentsJson()).at("/name").asText()).isEqualTo("redis");
		});
		assertThat(response.usage()).isEqualTo(new Usage(900, 40, 0, 850));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "\"end_turn\" | END_TURN", "\"tool_use\" | TOOL_USE",
			"\"max_tokens\" | MAX_TOKENS", "\"refusal\" | REFUSAL",
			"\"model_context_window_exceeded\" | CONTEXT_WINDOW_EXCEEDED", "\"pause_turn\" | OTHER", "null | OTHER" })
	void mapsEveryStopReason(String wireValue, StopReason expected) {
		String json = TOOL_USE_RESPONSE.replace("\"stop_reason\": \"tool_use\"", "\"stop_reason\": " + wireValue);

		assertThat(mapper.toResponse(message(json)).stopReason()).isEqualTo(expected);
	}

	@Test
	void historyOnlyGrowsAtTheEndAndReplaysAssistantBlocksVerbatim() {
		List<LlmMessage> history = new ArrayList<>(List.of(new LlmMessage.User("Redis neden durdu?")));
		JsonNode first = body(request(history));

		history.add(mapper.toResponse(message(TOOL_USE_RESPONSE)).message());
		history.add(new LlmMessage.ToolResults(List.of(new ToolResult("toolu_1", "exited (137)", false))));
		JsonNode second = body(request(history));

		assertThat(second.at("/system")).isEqualTo(first.at("/system"));
		assertThat(second.at("/tools")).isEqualTo(first.at("/tools"));
		assertThat(second.at("/messages/0")).isEqualTo(first.at("/messages/0"));
		// Same blocks, same order as the API returned them; the empty thinking block is kept with its signature.
		JsonNode replayed = second.at("/messages/1/content");
		assertThat(replayed).isEqualTo(json(TOOL_USE_RESPONSE).at("/content"));
		assertThat(replayed.at("/0/type").asText()).isEqualTo("thinking");
		assertThat(replayed.at("/0/thinking").asText()).isEmpty();
		assertThat(replayed.at("/0/signature").asText()).isEqualTo("sig-1");
	}

	@Test
	void sendsToolResultsTrimmedAndWithTheirErrorFlag() {
		List<LlmMessage> history = List.of(new LlmMessage.User("Redis neden durdu?"),
				mapper.toResponse(message(TOOL_USE_RESPONSE)).message(),
				new LlmMessage.ToolResults(List.of(new ToolResult("toolu_1", "e".repeat(100), true))));

		JsonNode result = body(request(history)).at("/messages/2/content/0");

		assertThat(result.at("/type").asText()).isEqualTo("tool_result");
		assertThat(result.at("/tool_use_id").asText()).isEqualTo("toolu_1");
		assertThat(result.at("/is_error").asBoolean()).isTrue();
		assertThat(result.at("/content").asText()).startsWith("e".repeat(40))
			.endsWith("[truncated: 40 of 100 characters shown]");
	}

	@Test
	void refusesToolResultsWithoutContent() {
		assertThatNullPointerException().isThrownBy(() -> new ToolResult("toolu_1", null, false));
	}

	@Test
	void trimsLongToolOutputDeterministically() {
		String output = "x".repeat(100);

		assertThat(mapper.trim(output)).isEqualTo(mapper.trim(output))
			.startsWith("x".repeat(40))
			.endsWith("[truncated: 40 of 100 characters shown]");
		assertThat(mapper.trim("short")).isEqualTo("short");
	}

	@Test
	void neverSplitsASurrogatePairWhenTrimming() {
		// 39 chars, then an emoji (two UTF-16 chars) straddling the 40-char limit.
		String output = "x".repeat(39) + "😀" + "x".repeat(60);

		assertThat(mapper.trim(output)).isEqualTo("x".repeat(39) + "\n[truncated: 39 of 101 characters shown]");
	}

	private LlmRequest request(List<LlmMessage> messages) {
		return new LlmRequest(taskId, "You are Alfred.", List.of(CONTAINER_STATUS), messages);
	}

	private JsonNode body(LlmRequest request) {
		return ObjectMappers.jsonMapper().valueToTree(mapper.toParams(request)._body());
	}

	static JsonNode json(String json) {
		try {
			return ObjectMappers.jsonMapper().readTree(json);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	static BetaMessage message(String json) {
		try {
			return ObjectMappers.jsonMapper().readValue(json, BetaMessage.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
