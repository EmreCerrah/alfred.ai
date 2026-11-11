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
	@ValueSource(strings = { "llm.unavailable", "llm.rejected", "agent.failed", "agent.busy", "agent.message-blank",
			"llm.budget-exhausted", "llm.budget-exhausted-mid-task", "llm.budget-warning", "conversation.reset",
			"conversation.suggest-reset", "conversation.context-full" })
	void hasEveryTextTheAgentAsksFor(String key) {
		assertThat(persona.text(key)).containsIgnoringCase("efendim").doesNotContain("Sayın");
	}

	@Test
	void formatsArgumentsWithoutLosingApostrophesOrGroupingDigits() {
		assertThat(persona.text("agent.message-too-long", 20000)).contains("20000").startsWith("Efendim");
		assertThat(persona.text("llm.turn-limit", 15)).contains("15 adım").startsWith("Efendim");
	}

	@ParameterizedTest
	@ValueSource(strings = { "yeni konuşma", "Yeni Konuşma.", "  YENİ KONUŞMA  ", "yeni konuşma!", "yeni konusma",
			"YENI KONUSMA", "Yeni Konusma", "Yeni konusma.", "yeni konuşma!!", "yeni konuşma .", "\tyeni konuşma\n",
			// decomposed ş (s + combining cedilla), as some keyboards and terminals send it
			"yeni konus\u0327ma" })
	void recognisesTheNewConversationCommand(String input) {
		assertThat(persona.isCommand("command.new-conversation", input)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "yeni konuşma başlat", "yeni bir konuşma", "yeni konuşma?", "yeni", "konuşma",
			"bir yeni konuşma", "yeni  konuşma", "yeni konuşma, lütfen", "" })
	void doesNotTakeASimilarSentenceForTheCommand(String input) {
		assertThat(persona.isCommand("command.new-conversation", input)).isFalse();
	}

	@Test
	void hasNoCommandForNullAndFailsLoudlyOnAnUnknownCommandKey() {
		assertThat(persona.isCommand("command.new-conversation", null)).isFalse();
		assertThatIllegalArgumentException().isThrownBy(() -> persona.isCommand("command.no-such", "x"));
	}

	@Test
	void pointsToTheNewConversationWayOutWhereALongConversationMayBeTheCause() {
		assertThat(persona.text("llm.rejected")).contains("yeni konuşma");
		assertThat(persona.text("conversation.context-full")).contains("yeni konuşma");
		assertThat(persona.text("conversation.suggest-reset")).contains("yeni konuşma");
	}

	@Test
	void tellsTheModelItRemembersButOnlyTheUserCanReset() {
		assertThat(persona.systemPrompt()).contains("Hafızan").contains("yeni konuşma");
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
