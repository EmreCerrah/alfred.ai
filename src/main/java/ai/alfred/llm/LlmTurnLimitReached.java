package ai.alfred.llm;

/**
 * The Task already made {@code alfred.llm.max-turns-per-task} calls; the next one was not made (FR-18).
 */
public class LlmTurnLimitReached extends RuntimeException {

	private final int limit;

	public LlmTurnLimitReached(String message, int limit) {
		super(message);
		this.limit = limit;
	}

	public int limit() {
		return limit;
	}

}
