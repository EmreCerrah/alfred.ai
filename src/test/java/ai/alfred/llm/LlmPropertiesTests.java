package ai.alfred.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

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
	void bindsTheShippedBudgetAndPricesFromApplicationYml() throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
			.forEach(environment.getPropertySources()::addLast);

		MeteringProperties metering = new Binder(ConfigurationPropertySources.get(environment),
				new PropertySourcesPlaceholdersResolver(environment))
			.bind("alfred.llm", MeteringProperties.class)
			.get();

		assertThat(metering.monthlyBudgetUsd()).isEqualByComparingTo("5");
		assertThat(metering.maxTurnsPerTask()).isEqualTo(15);
		assertThat(metering.budgetWarningRatio()).isEqualByComparingTo("0.8");
		assertThat(metering.budgetZone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
		assertThat(metering.pricing()).usingRecursiveComparison()
			.withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
			.isEqualTo(PricingTests.HAIKU_5_5);
	}

	@Test
	void rejectsABudgetOrTurnLimitThatCannotWork() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MeteringProperties(BigDecimal.ZERO, 15,
				new BigDecimal("0.8"), ZoneId.of("UTC"), PricingTests.HAIKU_5_5));
		assertThatIllegalArgumentException().isThrownBy(() -> new MeteringProperties(BigDecimal.ONE, 0,
				new BigDecimal("0.8"), ZoneId.of("UTC"), PricingTests.HAIKU_5_5));
		assertThatIllegalArgumentException().isThrownBy(() -> new MeteringProperties(BigDecimal.ONE, 15,
				new BigDecimal("1.5"), ZoneId.of("UTC"), PricingTests.HAIKU_5_5));
	}

	@Test
	void rejectsNegativeOrMissingPricesAndAZeroThreshold() {
		BigDecimal one = BigDecimal.ONE;
		assertThatIllegalArgumentException().isThrownBy(
				() -> new MeteringProperties.Prices(new BigDecimal("-0.1"), one, one, one, 100_000, one, one));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MeteringProperties.Prices(null, one, one, one, 100_000, one, one));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MeteringProperties.Prices(one, one, one, one, 0, one, one));
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
