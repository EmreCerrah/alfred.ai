package ai.alfred.client;

/**
 * The daemon cannot be reached or its runtime file cannot be read.
 */
class DaemonNotRunning extends Exception {

	DaemonNotRunning(String message) {
		super(message);
	}

}
