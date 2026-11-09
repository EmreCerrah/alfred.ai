package ai.alfred.llm;

/**
 * The model's next turn. Append {@link #message()} to the Conversation as is. {@code budgetWarning} is set on the one
 * response, per month, that pushed the spend past {@code alfred.llm.budget-warning-ratio} of the budget (FR-18).
 */
public record LlmResponse(LlmMessage.Assistant message, StopReason stopReason, Usage usage, boolean budgetWarning) {

	public LlmResponse(LlmMessage.Assistant message, StopReason stopReason, Usage usage) {
		this(message, stopReason, usage, false);
	}

	LlmResponse withBudgetWarning() {
		return new LlmResponse(message, stopReason, usage, true);
	}

}
