package ai.alfred.llm;

import java.util.Objects;

/**
 * A Tool the model may ask for. {@code inputSchemaJson} is a JSON Schema object.
 */
public record ToolDefinition(String name, String description, String inputSchemaJson) {

	public ToolDefinition {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(inputSchemaJson, "inputSchemaJson");
	}

}
