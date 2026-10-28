package ai.alfred.cli;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import ai.alfred.agent.AgentOutput;
import ai.alfred.agent.AgentOutputListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes everything the agent says to every connected client as Server-Sent Events: {@code reply} and {@code error},
 * each with {@code taskId} and {@code text}.
 */
@Component
class EventStream implements AgentOutputListener {

	private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	SseEmitter subscribe() throws IOException {
		SseEmitter emitter = new SseEmitter(0L);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(error -> emitters.remove(emitter));
		emitters.add(emitter);
		// Flushes the response headers, so a client knows it is subscribed before it posts a message.
		emitter.send(SseEmitter.event().comment("connected"));
		return emitter;
	}

	@Override
	public void onOutput(AgentOutput output) {
		String name = switch (output) {
			case AgentOutput.Reply reply -> "reply";
			case AgentOutput.Failure failure -> "error";
		};
		Map<String, String> data = Map.of("taskId", output.taskId().toString(), "text", output.text());
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
			}
			catch (IOException | IllegalStateException ex) {
				emitters.remove(emitter);
				emitter.completeWithError(ex);
			}
		}
	}

	int subscribers() {
		return emitters.size();
	}

}
