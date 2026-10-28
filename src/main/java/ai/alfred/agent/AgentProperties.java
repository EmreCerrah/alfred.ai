package ai.alfred.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code alfred.agent.*}.
 */
@ConfigurationProperties("alfred.agent")
record AgentProperties(int maxActiveTasks, int maxMessageChars) {

	AgentProperties {
		if (maxActiveTasks < 1) {
			throw new IllegalArgumentException("alfred.agent.max-active-tasks must be at least 1");
		}
		if (maxMessageChars < 1) {
			throw new IllegalArgumentException("alfred.agent.max-message-chars must be positive");
		}
	}

}
