package ai.alfred.agent;

/**
 * The user's message cannot become a Task (blank or too long); the message is Alfred's explanation.
 */
public class InvalidMessage extends RuntimeException {

	public InvalidMessage(String message) {
		super(message);
	}

}
