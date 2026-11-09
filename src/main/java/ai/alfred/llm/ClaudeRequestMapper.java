package ai.alfred.llm;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaThinkingBlockBinding;
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive;
import com.anthropic.models.beta.messages.BetaThinkingPrefixMismatchBehavior;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaUsage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Maps between alfred's domain types and the Anthropic beta Messages API. Deterministic and side-effect free, so the
 * same history always produces the same bytes (prompt cache, preserved thinking).
 */
class ClaudeRequestMapper {

	/** Lets the request set {@code thinking.block_binding}; required for an explicit mismatch behavior. */
	static final String THINKING_BINDING_BETA = "thinking-binding-controls-2026-08-01";

	private final LlmProperties properties;

	ClaudeRequestMapper(LlmProperties properties) {
		this.properties = properties;
	}

	MessageCreateParams toParams(LlmRequest request) {
		MessageCreateParams.Builder builder = MessageCreateParams.builder()
			.model(properties.model())
			.maxTokens(properties.maxTokens())
			.addBeta(THINKING_BINDING_BETA)
			// An edited history can only be a bug on our side: fail loudly instead of silently dropping reasoning.
			.thinking(BetaThinkingConfigAdaptive.builder()
				.blockBinding(BetaThinkingBlockBinding.builder()
					.prefixMismatchBehavior(BetaThinkingPrefixMismatchBehavior.ERROR)
					.build())
				.build())
			.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(properties.effort())).build())
			// Automatic breakpoint on the last cacheable block: caches the growing history of a tool loop without
			// touching the bytes of earlier messages.
			.cacheControl(BetaCacheControlEphemeral.builder().build())
			// Render order is tools -> system -> messages, so this breakpoint caches the whole fixed prefix.
			.systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder()
				.text(request.system())
				.cacheControl(BetaCacheControlEphemeral.builder().build())
				.build()));
		request.tools().forEach(tool -> builder.addTool(toTool(tool)));
		builder.messages(request.messages().stream().map(this::toParam).toList());
		return builder.build();
	}

	/** Length of the request body as JSON, for the token estimate behind the budget reservation. */
	long bodyLength(LlmRequest request) {
		try {
			return ObjectMappers.jsonMapper().writeValueAsString(toParams(request)._body()).length();
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("request body is not serializable", ex);
		}
	}

	LlmResponse toResponse(BetaMessage message) {
		List<BetaContentBlockParam> blocks = message.content().stream().map(BetaContentBlock::toParam).toList();
		String text = message.content()
			.stream()
			.filter(BetaContentBlock::isText)
			.map(block -> block.asText().text())
			.collect(Collectors.joining("\n"));
		List<ToolCall> toolCalls = message.content()
			.stream()
			.filter(BetaContentBlock::isToolUse)
			.map(BetaContentBlock::asToolUse)
			.map(use -> new ToolCall(use.id(), use.name(), json(use._input())))
			.toList();
		return new LlmResponse(new LlmMessage.Assistant(text, toolCalls, new ProviderContent(blocks)),
				stopReason(message), usage(message.usage()));
	}

	/**
	 * Deterministic, so a trimmed result is byte-identical on every later request (append-only history).
	 */
	String trim(String content) {
		int max = properties.maxToolResultChars();
		if (content.length() <= max) {
			return content;
		}
		// Never cut a surrogate pair in half: a lone surrogate is not valid UTF-8.
		int end = Character.isHighSurrogate(content.charAt(max - 1)) ? max - 1 : max;
		return content.substring(0, end)
				+ "\n[truncated: %d of %d characters shown]".formatted(end, content.length());
	}

	private BetaMessageParam toParam(LlmMessage message) {
		return switch (message) {
			case LlmMessage.User user ->
				BetaMessageParam.builder().role(BetaMessageParam.Role.USER).content(user.text()).build();
			case LlmMessage.Assistant assistant -> BetaMessageParam.builder()
				.role(BetaMessageParam.Role.ASSISTANT)
				.contentOfBetaContentBlockParams(assistantBlocks(assistant))
				.build();
			case LlmMessage.ToolResults results -> BetaMessageParam.builder()
				.role(BetaMessageParam.Role.USER)
				.contentOfBetaContentBlockParams(results.results()
					.stream()
					.map(result -> BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
						.toolUseId(result.toolCallId())
						.content(trim(result.content()))
						.isError(result.error())
						.build()))
					.toList())
				.build();
		};
	}

	@SuppressWarnings("unchecked")
	private static List<BetaContentBlockParam> assistantBlocks(LlmMessage.Assistant assistant) {
		// Replayed exactly as the API returned them: same blocks, same order, empty thinking text included.
		return (List<BetaContentBlockParam>) assistant.content().blocks();
	}

	private static BetaTool toTool(ToolDefinition tool) {
		Map<String, Object> schema = parse(tool.inputSchemaJson());
		if (schema == null || !"object".equals(schema.get("type"))) {
			throw new IllegalArgumentException(
					"inputSchemaJson of tool '%s' must be a JSON Schema of type object".formatted(tool.name()));
		}
		BetaTool.InputSchema.Builder inputSchema = BetaTool.InputSchema.builder();
		schema.forEach((key, value) -> {
			if (!"type".equals(key)) {
				inputSchema.putAdditionalProperty(key, JsonValue.from(value));
			}
		});
		return BetaTool.builder()
			.name(tool.name())
			.description(tool.description())
			.inputSchema(inputSchema.build())
			.build();
	}

	private static StopReason stopReason(BetaMessage message) {
		return message.stopReason().map(ClaudeRequestMapper::stopReason).orElse(StopReason.OTHER);
	}

	private static StopReason stopReason(BetaStopReason reason) {
		if (reason.equals(BetaStopReason.END_TURN)) {
			return StopReason.END_TURN;
		}
		if (reason.equals(BetaStopReason.TOOL_USE)) {
			return StopReason.TOOL_USE;
		}
		if (reason.equals(BetaStopReason.MAX_TOKENS)) {
			return StopReason.MAX_TOKENS;
		}
		if (reason.equals(BetaStopReason.REFUSAL)) {
			return StopReason.REFUSAL;
		}
		if (reason.equals(BetaStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED)) {
			return StopReason.CONTEXT_WINDOW_EXCEEDED;
		}
		return StopReason.OTHER;
	}

	private static Usage usage(BetaUsage usage) {
		return new Usage(usage.inputTokens(), usage.outputTokens(), usage.cacheReadInputTokens().orElse(0L),
				usage.cacheCreationInputTokens().orElse(0L));
	}

	private static Map<String, Object> parse(String json) {
		try {
			return ObjectMappers.jsonMapper().readValue(json, new TypeReference<>() {
			});
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("inputSchemaJson is not a JSON object", ex);
		}
	}

	private static String json(JsonValue value) {
		try {
			return ObjectMappers.jsonMapper().writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("tool input is not serializable", ex);
		}
	}

}
