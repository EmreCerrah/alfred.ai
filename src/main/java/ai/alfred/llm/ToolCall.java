package ai.alfred.llm;

import java.util.Objects;

/**
 * The model asking for a Tool. It is data only: the decision loop decides what happens next.
 */
public record ToolCall(String id, String name, String argumentsJson) {

	public ToolCall {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(argumentsJson, "argumentsJson");
	}

}
