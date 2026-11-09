package ai.alfred.llm;

/**
 * One raw call to the model, without budget or Turn checks. Only {@link MeteredLlmPort} uses it, so no call can
 * bypass the ledger (AD-12).
 */
interface ModelCall {

	LlmResponse call(LlmRequest request);

	/** An upper estimate of the prompt tokens, for the reservation made before the call. */
	long estimatePromptTokens(LlmRequest request);

	String model();

	long maxTokens();

}
