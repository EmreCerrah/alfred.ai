package ai.alfred.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PersonaTests {

	final Persona persona = Persona.load("tr");

	@Test
	void loadsTheTurkishSystemPromptFromResources() {
		assertThat(persona.systemPrompt()).startsWith("Sen Alfred'sin").contains("efendim");
	}

	@ParameterizedTest
	@ValueSource(strings = { "llm.unavailable", "llm.rejected", "agent.failed", "agent.busy", "agent.message-blank" })
	void hasEveryTextTheAgentAsksFor(String key) {
		assertThat(persona.text(key)).containsIgnoringCase("efendim").doesNotContain("Sayın");
	}

	@Test
	void formatsArgumentsWithoutLosingApostrophesOrGroupingDigits() {
		assertThat(persona.text("agent.message-too-long", 20000)).contains("20000").startsWith("Efendim");
	}

	@Test
	void failsLoudlyOnAnUnknownKey() {
		assertThatIllegalArgumentException().isThrownBy(() -> persona.text("no.such.key"));
	}

	@Test
	void failsAtStartupForAnUnknownLanguage() {
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> Persona.load("xx")))
			.hasMessageContaining("persona/xx/");
	}

}
