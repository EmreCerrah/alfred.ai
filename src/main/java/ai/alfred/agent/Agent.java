package ai.alfred.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import ai.alfred.llm.LlmBudgetExhausted;
import ai.alfred.llm.LlmMessage;
import ai.alfred.llm.LlmPort;
import ai.alfred.llm.LlmRequest;
import ai.alfred.llm.LlmRequestRejected;
import ai.alfred.llm.LlmResponse;
import ai.alfred.llm.LlmTurnLimitReached;
import ai.alfred.llm.LlmUnavailable;
import ai.alfred.llm.StopReason;
import ai.alfred.memory.Conversation;
import ai.alfred.persona.Persona;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * The decision loop's entry point. Each request is one LLM turn on top of the shared Conversation (FR-5), without
 * Tools yet (1.8). Only a completed exchange (the user's message and a keepable answer) is appended, so a failed
 * request leaves no trace and retyping it does not duplicate a turn. The "new conversation" command is handled here,
 * without Claude.
 */
@Service
@EnableConfigurationProperties(AgentProperties.class)
public class Agent implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(Agent.class);

	private static final String NEW_CONVERSATION = "command.new-conversation";

	private final LlmPort llm;

	private final Persona persona;

	private final Conversation<LlmMessage> conversation;

	private final List<AgentOutputListener> listeners;

	private final TaskRegistry tasks;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final int maxMessageChars;

	Agent(LlmPort llm, Persona persona, Conversation<LlmMessage> conversation, List<AgentOutputListener> listeners,
			AgentProperties properties) {
		this.llm = llm;
		this.persona = persona;
		this.conversation = conversation;
		this.listeners = List.copyOf(listeners);
		this.tasks = new TaskRegistry(properties.maxActiveTasks());
		this.maxMessageChars = properties.maxMessageChars();
	}

	/**
	 * Starts a Task for the user's message and returns at once; the answer arrives through
	 * {@link AgentOutputListener}.
	 * @throws AgentBusy when the active-Task limit is reached (the request is not queued)
	 * @throws InvalidMessage for a blank or too long message
	 */
	public UUID submit(String text) {
		if (text == null || text.isBlank()) {
			throw new InvalidMessage(persona.text("agent.message-blank"));
		}
		if (text.length() > maxMessageChars) {
			throw new InvalidMessage(persona.text("agent.message-too-long", maxMessageChars));
		}
		TaskRegistry.Task task = tasks.tryStart().orElseThrow(() -> new AgentBusy(persona.text("agent.busy")));
		try {
			executor.execute(() -> run(task.taskId(), text));
		}
		catch (RejectedExecutionException ex) {
			// Shutting down: give the slot back instead of leaking it.
			tasks.finish(task.taskId());
			throw ex;
		}
		return task.taskId();
	}

	int activeTasks() {
		return tasks.activeCount();
	}

	private void run(UUID taskId, String text) {
		AgentOutput output;
		try {
			output = persona.isCommand(NEW_CONVERSATION, text) ? startNewConversation(taskId) : answer(taskId, text);
		}
		catch (LlmBudgetExhausted ex) {
			log.warn("taskId={} budget exhausted: {}", taskId, ex.getMessage());
			// No Turn yet means the Task never started; otherwise it stopped halfway (FR-9).
			output = new AgentOutput.Failure(taskId, persona.text(ex.turnsUsed() == 0 ? "llm.budget-exhausted"
					: "llm.budget-exhausted-mid-task"));
		}
		catch (LlmTurnLimitReached ex) {
			log.warn("taskId={} {}", taskId, ex.getMessage());
			output = new AgentOutput.Failure(taskId, persona.text("llm.turn-limit", ex.limit()));
		}
		catch (LlmUnavailable ex) {
			log.warn("taskId={} Claude unavailable: {}", taskId, ex.getMessage());
			output = new AgentOutput.Failure(taskId, persona.text("llm.unavailable"));
		}
		catch (LlmRequestRejected ex) {
			log.error("taskId={} Claude rejected the request", taskId, ex);
			output = new AgentOutput.Failure(taskId, persona.text("llm.rejected"));
		}
		catch (RuntimeException | Error ex) {
			log.error("taskId={} Task failed", taskId, ex);
			output = new AgentOutput.Failure(taskId, persona.text("agent.failed"));
		}
		finally {
			// Free the slot before answering, so a client that replies at once is not told "busy".
			tasks.finish(taskId);
		}
		emit(output);
	}

	/** "New conversation": an ordinary Task that never calls Claude, so it works with an exhausted budget too. */
	private AgentOutput startNewConversation(UUID taskId) {
		conversation.reset();
		log.info("taskId={} Conversation reset by the user", taskId);
		return new AgentOutput.Reply(taskId, persona.text("conversation.reset"));
	}

	private AgentOutput answer(UUID taskId, String text) {
		LlmMessage.User question = new LlmMessage.User(text);
		List<LlmMessage> messages = new ArrayList<>(conversation.messages());
		messages.add(question);
		LlmResponse response = llm.complete(new LlmRequest(taskId, persona.systemPrompt(), List.of(), messages));

		if (response.stopReason() == StopReason.CONTEXT_WINDOW_EXCEEDED) {
			log.warn("taskId={} Conversation no longer fits the context window", taskId);
			return new AgentOutput.Failure(taskId, withBudgetWarning(persona.text("conversation.context-full"), response));
		}
		String answer = response.message().text();
		if (keepable(response)) {
			// The assistant turn goes in as returned: its ProviderContent carries the thinking blocks in order (AD-11).
			conversation.append(question, response.message());
		}
		else {
			// An empty or unfinished assistant turn would make every later call fail; the exchange is not kept.
			log.warn("taskId={} stop reason {}: answer not kept in the Conversation", taskId, response.stopReason());
		}
		if (answer.isBlank()) {
			return new AgentOutput.Failure(taskId, withBudgetWarning(persona.text("agent.failed"), response));
		}
		String reply = withBudgetWarning(answer, response);
		if (conversation.claimResetSuggestion(response.usage().promptTokens())) {
			reply += "\n\n" + persona.text("conversation.suggest-reset");
		}
		return new AgentOutput.Reply(taskId, reply);
	}

	/** The once-a-month warning is used up by this call, so it is shown whatever the outcome. */
	private String withBudgetWarning(String text, LlmResponse response) {
		return response.budgetWarning() ? text + "\n\n" + persona.text("llm.budget-warning") : text;
	}

	/** A finished or token-limited answer with text; refusals, unknown stops and empty turns stay out. */
	private static boolean keepable(LlmResponse response) {
		boolean finished = response.stopReason() == StopReason.END_TURN
				|| response.stopReason() == StopReason.MAX_TOKENS;
		return finished && !response.message().text().isBlank();
	}

	private void emit(AgentOutput output) {
		for (AgentOutputListener listener : listeners) {
			try {
				listener.onOutput(output);
			}
			catch (RuntimeException ex) {
				log.warn("taskId={} output listener {} failed", output.taskId(), listener.getClass().getName(), ex);
			}
		}
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}

}
