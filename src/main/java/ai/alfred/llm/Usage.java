package ai.alfred.llm;

/**
 * Token usage of one call.
 */
public record Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
}
