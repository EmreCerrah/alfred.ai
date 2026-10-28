package ai.alfred.agent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import ai.alfred.llm.LlmMessage;
import ai.alfred.llm.LlmPort;
import ai.alfred.llm.LlmRequest;
import ai.alfred.llm.LlmRequestRejected;
import ai.alfred.llm.LlmResponse;
import ai.alfred.llm.LlmUnavailable;
import ai.alfred.persona.Persona;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * The decision loop's entry point. For now each request is a single LLM turn without memory or Tools; Conversation
 * (1.4), budget (1.5) and Tools (1.8) build on this.
 */
@Service
@EnableConfigurationProperties(AgentProperties.class)
public class Agent implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(Agent.class);

	private final LlmPort llm;

	private final Persona persona;

	private final List<AgentOutputListener> listeners;

	private final TaskRegistry tasks;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final int maxMessageChars;

	Agent(LlmPort llm, Persona persona, List<AgentOutputListener> listeners, AgentProperties properties) {
		this.llm = llm;
		this.persona = persona;
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
			LlmResponse response = llm.complete(
					new LlmRequest(taskId, persona.systemPrompt(), List.of(), List.of(new LlmMessage.User(text))));
			output = new AgentOutput.Reply(taskId, response.message().text());
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
