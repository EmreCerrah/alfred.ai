package ai.alfred.llm;

/**
 * Why the model stopped. {@link #REFUSAL} means the provider declined; there is no server-side fallback.
 */
public enum StopReason {

	END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL, CONTEXT_WINDOW_EXCEEDED, OTHER

}
