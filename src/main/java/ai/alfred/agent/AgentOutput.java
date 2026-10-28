package ai.alfred.agent;

import java.util.Objects;
import java.util.UUID;

/**
 * Something the agent says to the user, always tied to the Task it belongs to (AD-23).
 */
public sealed interface AgentOutput {

	UUID taskId();

	String text();

	/** The final answer of a Task. */
	record Reply(UUID taskId, String text) implements AgentOutput {

		public Reply {
			Objects.requireNonNull(taskId, "taskId");
			Objects.requireNonNull(text, "text");
		}

	}

	/** The Task ended without an answer; {@code text} explains why, in Alfred's words (FR-9). */
	record Failure(UUID taskId, String text) implements AgentOutput {

		public Failure {
			Objects.requireNonNull(taskId, "taskId");
			Objects.requireNonNull(text, "text");
		}

	}

}
