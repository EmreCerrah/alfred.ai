package ai.alfred.llm;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One call to the model. {@code system} and {@code tools} must stay identical for the whole Conversation, and
 * {@code messages} only ever grows at the end (AD-11).
 */
public record LlmRequest(UUID taskId, String system, List<ToolDefinition> tools, List<LlmMessage> messages) {

	public LlmRequest {
		Objects.requireNonNull(taskId, "taskId");
		Objects.requireNonNull(system, "system");
		tools = List.copyOf(tools);
		messages = List.copyOf(messages);
		if (messages.isEmpty()) {
			throw new IllegalArgumentException("messages must not be empty");
		}
		if (messages.getLast() instanceof LlmMessage.Assistant) {
			throw new IllegalArgumentException("messages must end with a user turn; assistant prefill is not supported");
		}
	}

}
