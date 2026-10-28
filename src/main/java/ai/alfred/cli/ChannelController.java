package ai.alfred.cli;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import ai.alfred.agent.Agent;
import ai.alfred.agent.AgentBusy;
import ai.alfred.agent.InvalidMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The daemon side of the CLI channel (AD-10): client to daemon by {@code POST}, daemon to client by SSE.
 */
@RestController
@RequestMapping("/api")
class ChannelController {

	record MessageRequest(String text) {
	}

	private final Agent agent;

	private final EventStream events;

	ChannelController(Agent agent, EventStream events) {
		this.agent = agent;
		this.events = events;
	}

	@GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter events() throws IOException {
		return events.subscribe();
	}

	@PostMapping("/messages")
	ResponseEntity<Map<String, UUID>> message(@RequestBody MessageRequest request) {
		UUID taskId = agent.submit(request.text());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("taskId", taskId));
	}

	@ExceptionHandler(AgentBusy.class)
	ProblemDetail busy(AgentBusy ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(InvalidMessage.class)
	ProblemDetail invalid(InvalidMessage ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

}
