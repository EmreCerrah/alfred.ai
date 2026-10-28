package ai.alfred.persona;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@code alfred.persona.*}: the language whose resources Alfred speaks with.
 */
@ConfigurationProperties("alfred.persona")
record PersonaProperties(String language) {

	PersonaProperties {
		if (language == null || language.isBlank()) {
			throw new IllegalArgumentException("alfred.persona.language must be set");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(PersonaProperties.class)
	static class PersonaConfiguration {

	}

}
