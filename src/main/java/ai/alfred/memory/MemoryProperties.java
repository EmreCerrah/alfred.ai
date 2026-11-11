package ai.alfred.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code alfred.memory.*}: {@code suggest-reset-tokens} is the prompt size (input + cache read + cache write) above
 * which Alfred suggests a new Conversation once.
 */
@ConfigurationProperties("alfred.memory")
record MemoryProperties(long suggestResetTokens) {

	MemoryProperties {
		if (suggestResetTokens < 1) {
			throw new IllegalArgumentException("alfred.memory.suggest-reset-tokens must be positive");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(MemoryProperties.class)
	static class MemoryConfiguration {

		/** The single, shared Conversation; its entry type is chosen by the module that injects it. */
		@Bean
		Conversation<?> conversation(MemoryProperties properties) {
			return new Conversation<>(properties.suggestResetTokens());
		}

	}

}
