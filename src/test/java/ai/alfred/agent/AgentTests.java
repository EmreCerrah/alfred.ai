package ai.alfred.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ai.alfred.llm.FakeLlmPort;
import ai.alfred.llm.LlmBudgetExhausted;
import ai.alfred.llm.LlmMessage;
import ai.alfred.llm.LlmRequestRejected;
import ai.alfred.llm.LlmTurnLimitReached;
import ai.alfred.llm.LlmUnavailable;
import ai.alfred.llm.StopReason;
import ai.alfred.llm.Usage;
import ai.alfred.memory.Conversation;
import ai.alfred.persona.Persona;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentTests {

	static final long SUGGEST_RESET_TOKENS = 1000;

	final Persona persona = Persona.load("tr");

	final FakeLlmPort llm = new FakeLlmPort();

	final Conversation<LlmMessage> conversation = new Conversation<>(SUGGEST_RESET_TOKENS);

	final LinkedBlockingQueue<AgentOutput> outputs = new LinkedBlockingQueue<>();

	final Agent agent = new Agent(llm, persona, conversation, List.of(outputs::add),
			new AgentProperties(1, 50));

	@AfterEach
	void stop() {
		agent.destroy();
	}

	@Test
	void runsEachMessageAsATaskAndRepliesWithItsTaskId() throws Exception {
		llm.replyWith("Buyurun, efendim.");

		UUID taskId = agent.submit("Merhaba alfred");

		assertThat(next()).isEqualTo(new AgentOutput.Reply(taskId, "Buyurun, efendim."));
		assertThat(llm.lastRequest().taskId()).isEqualTo(taskId);
		assertThat(llm.lastRequest().system()).isEqualTo(persona.systemPrompt());
		assertThat(llm.lastRequest().tools()).isEmpty();
		assertThat(llm.lastRequest().messages()).containsExactly(new LlmMessage.User("Merhaba alfred"));
		awaitNoActiveTasks();
	}

	@Test
	void explainsAnUnreachableClaudeInAlfredsWords() throws Exception {
		llm.failWith(FakeLlmPort.unavailable());

		UUID taskId = agent.submit("Merhaba");

		assertThat(next()).isEqualTo(new AgentOutput.Failure(taskId, persona.text("llm.unavailable")));
		awaitNoActiveTasks();
	}

	@Test
	void reportsARejectedRequestAndAnUnexpectedFailure() throws Exception {
		llm.failWith(new LlmRequestRejected("400", null));
		UUID rejected = agent.submit("Merhaba");
		assertThat(next()).isEqualTo(new AgentOutput.Failure(rejected, persona.text("llm.rejected")));
		awaitNoActiveTasks();

		llm.failWith(new IllegalStateException("boom"));
		UUID failed = agent.submit("Merhaba");
		assertThat(next()).isEqualTo(new AgentOutput.Failure(failed, persona.text("agent.failed")));
		awaitNoActiveTasks();
	}

	@Test
	void refusesASecondTaskWhileOneIsActiveAndAcceptsAgainAfterwards() throws Exception {
		llm.hold();
		UUID first = agent.submit("Bir");

		assertThatExceptionOfType(AgentBusy.class).isThrownBy(() -> agent.submit("Iki"))
			.withMessage(persona.text("agent.busy"));

		llm.release();
		assertThat(next().taskId()).isEqualTo(first);
		awaitNoActiveTasks();
		assertThat(agent.submit("Uc")).isNotEqualTo(first);
		next();
	}

	@Test
	void explainsAnExhaustedBudgetAsNoNewTaskOrAsAStoppedTask() throws Exception {
		llm.failWith(new LlmBudgetExhausted("full", 0));
		UUID refused = agent.submit("Merhaba");
		assertThat(next()).isEqualTo(new AgentOutput.Failure(refused, persona.text("llm.budget-exhausted")));

		llm.failWith(new LlmBudgetExhausted("full", 3));
		UUID stopped = agent.submit("Merhaba");
		assertThat(next()).isEqualTo(new AgentOutput.Failure(stopped, persona.text("llm.budget-exhausted-mid-task")));
	}

	@Test
	void reportsTheTurnLimitWithTheLimit() throws Exception {
		llm.failWith(new LlmTurnLimitReached("limit", 15));
		UUID taskId = agent.submit("Merhaba");

		AgentOutput output = next();
		assertThat(output).isEqualTo(new AgentOutput.Failure(taskId, persona.text("llm.turn-limit", 15)));
		assertThat(output.text()).contains("15");
	}

	@Test
	void appendsTheBudgetWarningToTheReplyOnce() throws Exception {
		llm.replyWithBudgetWarning("Buyurun, efendim.");

		UUID taskId = agent.submit("Merhaba");

		assertThat(next()).isEqualTo(new AgentOutput.Reply(taskId,
				"Buyurun, efendim.\n\n" + persona.text("llm.budget-warning")));
	}

	@Test
	void rejectsBlankAndTooLongMessagesInAlfredsWords() {
		assertThatExceptionOfType(InvalidMessage.class).isThrownBy(() -> agent.submit("  "))
			.withMessage(persona.text("agent.message-blank"));
		assertThatExceptionOfType(InvalidMessage.class).isThrownBy(() -> agent.submit("x".repeat(51)))
			.withMessage(persona.text("agent.message-too-long", 50))
			.withMessageContaining("50");
		assertThat(agent.activeTasks()).isZero();
		assertThat(llm.lastRequest()).isNull();
	}

	@Test
	void freesTheSlotBeforeAnsweringSoAnImmediateFollowUpIsAccepted() throws Exception {
		UUID first = agent.submit("Bir");
		assertThat(next().taskId()).isEqualTo(first);

		// No waiting: by the time the reply is out, the Task has left the registry.
		assertThat(agent.activeTasks()).isZero();
		assertThat(agent.submit("Iki")).isNotEqualTo(first);
		next();
	}

	@Test
	void aFailingListenerDoesNotSilenceTheOthers() throws Exception {
		Agent withBrokenListener = new Agent(llm, persona, conversation, List.of(output -> {
			throw new IllegalStateException("broken");
		}, outputs::add), new AgentProperties(1, 50));
		try {
			UUID taskId = withBrokenListener.submit("Merhaba");
			assertThat(next().taskId()).isEqualTo(taskId);
		}
		finally {
			withBrokenListener.destroy();
		}
	}

	@Test
	void sendsThePreviousExchangeSoAReferenceCanBeResolved() throws Exception {
		llm.replyWith("Adı redis-cache, efendim.");
		ask("Redis container'ının adı ne?");
		LlmMessage.Assistant firstAnswer = llm.lastResponse().message();

		llm.replyWith("Loglar temiz, efendim.");
		ask("Onun loglarını özetle");

		List<LlmMessage> sent = llm.lastRequest().messages();
		assertThat(sent).hasSize(3);
		assertThat(sent.get(0)).isEqualTo(new LlmMessage.User("Redis container'ının adı ne?"));
		assertThat(sent.get(1)).as("the assistant turn is replayed as returned").isSameAs(firstAnswer);
		assertThat(sent.get(2)).isEqualTo(new LlmMessage.User("Onun loglarını özetle"));
		assertThat(llm.lastRequest().system()).isEqualTo(persona.systemPrompt());
		assertThat(llm.lastRequest().tools()).isEmpty();
	}

	@Test
	void eachRequestStartsWithThePreviousRequestUnchanged() throws Exception {
		ask("Bir");
		ask("Iki");
		ask("Uc");

		List<List<LlmMessage>> sent = llm.requests().stream().map(request -> request.messages()).toList();
		assertThat(sent).hasSize(3);
		assertThat(sent.get(1).subList(0, sent.get(0).size())).isEqualTo(sent.get(0));
		assertThat(sent.get(2).subList(0, sent.get(1).size())).isEqualTo(sent.get(1));
		assertThat(sent.get(2)).hasSize(5);
		assertThat(conversation.messages()).hasSize(6);
	}

	@Test
	void startsANewConversationWithoutCallingClaude() throws Exception {
		ask("Redis container'ının adı ne?");

		UUID reset = agent.submit("Yeni Konuşma.");

		assertThat(next()).isEqualTo(new AgentOutput.Reply(reset, persona.text("conversation.reset")));
		assertThat(llm.requests()).hasSize(1);
		assertThat(conversation.messages()).isEmpty();
		ask("Merhaba");
		assertThat(llm.lastRequest().messages()).containsExactly(new LlmMessage.User("Merhaba"));
	}

	@Test
	void startsANewConversationEvenWithTheBudgetExhausted() throws Exception {
		ask("Merhaba");
		llm.failWith(new LlmBudgetExhausted("full", 0));

		UUID reset = agent.submit("  yeni konusma! ");

		assertThat(next()).isEqualTo(new AgentOutput.Reply(reset, persona.text("conversation.reset")));
		assertThat(llm.requests()).hasSize(1);
		assertThat(conversation.messages()).isEmpty();
		awaitNoActiveTasks();
	}

	@Test
	void sendsASimilarButDifferentSentenceToClaude() throws Exception {
		ask("Merhaba");

		ask("yeni konuşma başlat");

		assertThat(llm.requests()).hasSize(2);
		assertThat(llm.lastRequest().messages()).hasSize(3);
		assertThat(conversation.messages()).hasSize(4);
	}

	@Test
	void refusesTheCommandLikeAnyMessageWhileATaskIsActive() throws Exception {
		ask("Merhaba");
		llm.hold();
		UUID first = agent.submit("Bir");

		assertThatExceptionOfType(AgentBusy.class).isThrownBy(() -> agent.submit("yeni konuşma"))
			.withMessage(persona.text("agent.busy"));

		llm.release();
		assertThat(next().taskId()).isEqualTo(first);
		assertThat(conversation.messages()).hasSize(4);
		awaitNoActiveTasks();
	}

	@Test
	void suggestsANewConversationOnceAfterTheThresholdAndKeepsTheHistory() throws Exception {
		// 300 input + 600 cache read + 200 cache write = 1100 prompt tokens > 1000
		Usage large = new Usage(300, 10, 600, 200);
		llm.replyWith("Birinci, efendim.", StopReason.END_TURN, large);
		assertThat(ask("Bir").text()).isEqualTo("Birinci, efendim.\n\n" + persona.text("conversation.suggest-reset"));

		llm.replyWith("Ikinci, efendim.", StopReason.END_TURN, large);
		assertThat(ask("Iki").text()).isEqualTo("Ikinci, efendim.");
		assertThat(conversation.messages()).hasSize(4);

		ask("yeni konuşma");
		assertThat(ask("Uc").text()).endsWith(persona.text("conversation.suggest-reset"));
	}

	@Test
	void doesNotSuggestANewConversationAtTheThreshold() throws Exception {
		llm.replyWith("Tamam, efendim.", StopReason.END_TURN, new Usage(400, 10, 400, 200));

		assertThat(ask("Bir").text()).isEqualTo("Tamam, efendim.");
	}

	@Test
	void putsTheResetSuggestionAfterTheBudgetWarning() throws Exception {
		llm.replyWithBudgetWarning("Buyurun, efendim.", new Usage(2000, 10, 0, 0));

		assertThat(ask("Bir").text()).isEqualTo("Buyurun, efendim.\n\n" + persona.text("llm.budget-warning") + "\n\n"
				+ persona.text("conversation.suggest-reset"));
	}

	@Test
	void leavesTheHistoryUntouchedWhenTheCallFails() throws Exception {
		ask("Merhaba");
		List<LlmMessage> before = conversation.messages();

		for (RuntimeException failure : List.of(new LlmUnavailable("down"), new LlmBudgetExhausted("full", 0),
				new LlmTurnLimitReached("limit", 15), new LlmRequestRejected("400", null),
				new IllegalStateException("boom"))) {
			llm.failWith(failure);
			assertThat(ask("Tekrar")).isInstanceOf(AgentOutput.Failure.class);
			assertThat(conversation.messages()).as(failure.getClass().getSimpleName()).isEqualTo(before);
		}
		llm.replyWith("Buyurun, efendim.");
		ask("Tekrar");
		assertThat(llm.lastRequest().messages()).hasSize(3);
	}

	@Test
	void keepsATruncatedAnswerThatHasText() throws Exception {
		llm.replyWith("Yarim kalan bir yanit", StopReason.MAX_TOKENS, FakeLlmPort.SMALL_USAGE);

		assertThat(ask("Bir")).isInstanceOf(AgentOutput.Reply.class).extracting(AgentOutput::text)
			.isEqualTo("Yarim kalan bir yanit");
		assertThat(conversation.messages()).hasSize(2);
	}

	@Test
	void showsButDoesNotKeepARefusalOrAnUnknownStop() throws Exception {
		llm.replyWith("Bunu yapamam, efendim.", StopReason.REFUSAL, FakeLlmPort.SMALL_USAGE);
		assertThat(ask("Bir")).isInstanceOf(AgentOutput.Reply.class).extracting(AgentOutput::text)
			.isEqualTo("Bunu yapamam, efendim.");

		llm.replyWith("Garip bir son.", StopReason.OTHER, FakeLlmPort.SMALL_USAGE);
		assertThat(ask("Iki")).isInstanceOf(AgentOutput.Reply.class);

		assertThat(conversation.messages()).isEmpty();
	}

	@Test
	void failsWithoutKeepingAnEmptyAnswer() throws Exception {
		for (StopReason stop : List.of(StopReason.END_TURN, StopReason.MAX_TOKENS, StopReason.REFUSAL,
				StopReason.OTHER)) {
			llm.replyWith("  ", stop, FakeLlmPort.SMALL_USAGE);
			UUID taskId = agent.submit("Bir");
			assertThat(next()).as(stop.name()).isEqualTo(new AgentOutput.Failure(taskId, persona.text("agent.failed")));
			awaitNoActiveTasks();
		}
		assertThat(conversation.messages()).isEmpty();
	}

	@Test
	void suggestsANewConversationWhenTheContextWindowIsFull() throws Exception {
		ask("Merhaba");
		llm.replyWith("Kesik", StopReason.CONTEXT_WINDOW_EXCEEDED, FakeLlmPort.SMALL_USAGE);

		UUID taskId = agent.submit("Devam");

		assertThat(next()).isEqualTo(new AgentOutput.Failure(taskId, persona.text("conversation.context-full")));
		assertThat(conversation.messages()).hasSize(2);
		awaitNoActiveTasks();
	}

	@Test
	void showsTheBudgetWarningWhenTheContextWindowIsFull() throws Exception {
		llm.replyWithBudgetWarning("Kesik", StopReason.CONTEXT_WINDOW_EXCEEDED, FakeLlmPort.SMALL_USAGE);

		UUID taskId = agent.submit("Devam");

		assertThat(next()).isEqualTo(new AgentOutput.Failure(taskId,
				persona.text("conversation.context-full") + "\n\n" + persona.text("llm.budget-warning")));
		awaitNoActiveTasks();
	}

	@Test
	void showsTheBudgetWarningWithAnEmptyAnswer() throws Exception {
		llm.replyWithBudgetWarning("  ", StopReason.END_TURN, FakeLlmPort.SMALL_USAGE);

		UUID taskId = agent.submit("Bir");

		assertThat(next()).isEqualTo(new AgentOutput.Failure(taskId,
				persona.text("agent.failed") + "\n\n" + persona.text("llm.budget-warning")));
		awaitNoActiveTasks();
	}

	/** Submits, waits for the output and for the slot to be free, so the next message is not refused as busy. */
	private AgentOutput ask(String text) throws InterruptedException {
		UUID taskId = agent.submit(text);
		AgentOutput output = next();
		assertThat(output.taskId()).isEqualTo(taskId);
		awaitNoActiveTasks();
		return output;
	}

	private AgentOutput next() throws InterruptedException {
		AgentOutput output = outputs.poll(10, TimeUnit.SECONDS);
		assertThat(output).as("agent output").isNotNull();
		return output;
	}

	private void awaitNoActiveTasks() throws InterruptedException {
		for (int i = 0; i < 100 && agent.activeTasks() > 0; i++) {
			Thread.sleep(20);
		}
		assertThat(agent.activeTasks()).isZero();
	}

}
