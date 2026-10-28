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
import ai.alfred.persona.Persona;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentTests {

	final Persona persona = Persona.load("tr");

	final FakeLlmPort llm = new FakeLlmPort();

	final LinkedBlockingQueue<AgentOutput> outputs = new LinkedBlockingQueue<>();

	final Agent agent = new Agent(llm, persona, List.of(outputs::add), new AgentProperties(1, 50));

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
		Agent withBrokenListener = new Agent(llm, persona, List.of(output -> {
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
