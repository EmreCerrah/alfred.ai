package ai.alfred.agent;

/**
 * A new request arrived while {@code alfred.agent.max-active-tasks} Tasks are running. It is not queued (AD-9); the
 * message is Alfred's explanation for the user.
 */
public class AgentBusy extends RuntimeException {

	public AgentBusy(String message) {
		super(message);
	}

}
