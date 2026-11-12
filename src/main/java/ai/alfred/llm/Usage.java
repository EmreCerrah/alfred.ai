package ai.alfred.llm;

/**
 * Token usage of one call.
 */
public record Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

	/**
	 * The size of the prompt the model read: input + cache read + cache write. The one place this sum is made; it picks
	 * the rate card and drives the Conversation reset suggestion.
	 */
	public long promptTokens() {
		return inputTokens + cacheReadTokens + cacheWriteTokens;
	}

}
