package ai.alfred.llm;

import java.util.List;
import java.util.Objects;

/**
 * A Conversation entry. Assistant turns are only ever produced by {@link LlmPort} and must be appended back unchanged:
 * their {@link ProviderContent} carries the provider's blocks (including thinking) in their original order.
 */
public sealed interface LlmMessage {

	record User(String text) implements LlmMessage {

		public User {
			Objects.requireNonNull(text, "text");
		}

	}

	record Assistant(String text, List<ToolCall> toolCalls, ProviderContent content) implements LlmMessage {

		public Assistant {
			Objects.requireNonNull(text, "text");
			toolCalls = List.copyOf(toolCalls);
			Objects.requireNonNull(content, "content");
		}

	}

	record ToolResults(List<ToolResult> results) implements LlmMessage {

		public ToolResults {
			results = List.copyOf(results);
			if (results.isEmpty()) {
				throw new IllegalArgumentException("results must not be empty");
			}
		}

	}

}
