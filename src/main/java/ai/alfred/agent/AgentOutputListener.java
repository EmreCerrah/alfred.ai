package ai.alfred.agent;

/**
 * Implemented by inbound adapters (CLI today, voice later) to receive what the agent says. Every listener gets every
 * output, so voice and CLI see the same Conversation (AD-9).
 */
public interface AgentOutputListener {

	void onOutput(AgentOutput output);

}
