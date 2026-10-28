package ai.alfred.llm;

/**
 * The model's next turn. Append {@link #message()} to the Conversation as is.
 */
public record LlmResponse(LlmMessage.Assistant message, StopReason stopReason, Usage usage) {
}
