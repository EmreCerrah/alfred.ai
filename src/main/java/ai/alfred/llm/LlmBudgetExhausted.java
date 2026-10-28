package ai.alfred.llm;

/**
 * The call would take the month over {@code alfred.llm.monthly-budget-usd}; it was not made (FR-18). {@link #turnsUsed()}
 * tells a refused first call (no new Task) apart from a Task stopped halfway (reported as FR-9).
 */
public class LlmBudgetExhausted extends RuntimeException {

	private final int turnsUsed;

	public LlmBudgetExhausted(String message, int turnsUsed) {
		super(message);
		this.turnsUsed = turnsUsed;
	}

	public int turnsUsed() {
		return turnsUsed;
	}

}
