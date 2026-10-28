package ai.alfred.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class LlmPropertiesTests {

	@Test
	void bindsTheShippedDefaultsFromApplicationYml() throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
			.forEach(environment.getPropertySources()::addLast);

		LlmProperties properties = new Binder(ConfigurationPropertySources.get(environment),
				new PropertySourcesPlaceholdersResolver(environment))
			.bind("alfred.llm", LlmProperties.class)
			.get();

		assertThat(properties.model()).isEqualTo("claude-haiku-5-5");
		assertThat(properties.effort()).isEqualTo("medium");
		assertThat(properties.maxTokens()).isEqualTo(16000);
		assertThat(properties.maxRetries()).isEqualTo(2);
		assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(60));
		assertThat(properties.maxToolResultChars()).isEqualTo(20000);
		assertThat(properties.hasApiKey()).isEqualTo(System.getenv("ANTHROPIC_API_KEY") != null
				&& !System.getenv("ANTHROPIC_API_KEY").isBlank());
	}

	@Test
	void rejectsAnUnknownEffortAtStartup() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LlmProperties("claude-haiku-5-5", "meduim", 16000,
				null, null, 2, Duration.ofSeconds(60), 20000))
			.withMessageContaining("alfred.llm.effort");
	}

	@Test
	void rejectsNonPositiveLimits() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> new LlmProperties("claude-haiku-5-5", "medium", 0, null, null, 2, Duration.ofSeconds(60), 20000));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new LlmProperties("claude-haiku-5-5", "medium", 16000, null, null, 2, Duration.ofSeconds(60), 0));
	}

	@Test
	void stripsWhitespaceAroundTheApiKeyAndNeverPrintsIt() {
		LlmProperties properties = new LlmProperties("claude-haiku-5-5", "medium", 16000, " sk-test\n", null, 2,
				Duration.ofSeconds(60), 20000);

		assertThat(properties.apiKey()).isEqualTo("sk-test");
		assertThat(properties.toString()).doesNotContain("sk-test");
	}

}
