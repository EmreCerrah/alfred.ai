package ai.alfred.llm;

import java.util.Objects;

/**
 * The outcome of a {@link ToolCall}, sent back to the model.
 */
public record ToolResult(String toolCallId, String content, boolean error) {

	public ToolResult {
		Objects.requireNonNull(toolCallId, "toolCallId");
		Objects.requireNonNull(content, "content");
	}

}
