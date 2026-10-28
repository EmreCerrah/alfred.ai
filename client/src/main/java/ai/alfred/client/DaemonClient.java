package ai.alfred.client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Sends one message and waits for the answer to its Task. Holds no business logic (FR-4): it only moves text between
 * the terminal and the daemon. A restarted daemon has a new token and port, so on {@code 401} or a refused connection
 * it re-reads the runtime file and tries once more.
 */
class DaemonClient {

	/** What came back for a message: Alfred's reply, his explanation of a failure, or a refusal of the request. */
	record Answer(Kind kind, String text) {

		enum Kind {

			REPLY, ERROR, REJECTED

		}

	}

	/** Longer than any single Task can take today (LLM timeout and retries), so a silent daemon cannot hang us. */
	static final Duration DEFAULT_ANSWER_TIMEOUT = Duration.ofMinutes(5);

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	/** Reads the runtime file; a seam so tests can play a daemon that restarts between two reads. */
	interface EndpointSource {

		DaemonEndpoint load() throws DaemonNotRunning;

	}

	private final EndpointSource endpoints;

	private final String runtimeFile;

	private final Duration answerTimeout;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	DaemonClient(Path runtimeFile) {
		this(runtimeFile, DEFAULT_ANSWER_TIMEOUT);
	}

	DaemonClient(Path runtimeFile, Duration answerTimeout) {
		this(() -> DaemonEndpoint.load(runtimeFile), runtimeFile.toString(), answerTimeout);
	}

	DaemonClient(EndpointSource endpoints, String runtimeFile, Duration answerTimeout) {
		this.endpoints = endpoints;
		this.runtimeFile = runtimeFile;
		this.answerTimeout = answerTimeout;
	}

	Answer send(String text) throws DaemonNotRunning, IOException, InterruptedException {
		DaemonEndpoint endpoint = endpoints.load();
		try {
			return attempt(endpoint, text);
		}
		catch (Unauthorized | Unreachable ex) {
			DaemonEndpoint reread = endpoints.load();
			if (ex instanceof Unreachable && reread.equals(endpoint)) {
				throw new DaemonNotRunning(
						"alfred daemon is not running (nothing listens on port " + endpoint.port() + ")");
			}
			try {
				return attempt(reread, text);
			}
			catch (Unauthorized again) {
				throw new DaemonNotRunning("The daemon rejected the token in " + runtimeFile);
			}
			catch (Unreachable again) {
				throw new DaemonNotRunning(
						"alfred daemon is not running (nothing listens on port " + reread.port() + ")");
			}
		}
	}

	private Answer attempt(DaemonEndpoint endpoint, String text)
			throws Unauthorized, Unreachable, IOException, InterruptedException {
		URI base = URI.create("http://127.0.0.1:" + endpoint.port());
		HttpResponse<Stream<String>> events;
		try {
			// Subscribe first: the daemon keeps no backlog, so a reply sent before we listen would be lost.
			events = http.send(request(base.resolve("/api/events"), endpoint).header("Accept", "text/event-stream")
				.GET()
				.build(), HttpResponse.BodyHandlers.ofLines());
		}
		catch (ConnectException ex) {
			throw new Unreachable();
		}
		try (Stream<String> lines = events.body()) {
			if (events.statusCode() == 401) {
				throw new Unauthorized();
			}
			if (events.statusCode() != 200) {
				throw new IOException("Unexpected status " + events.statusCode() + " from /api/events");
			}
			ObjectNode body = JSON.createObjectNode().put("text", text);
			HttpResponse<String> posted = http.send(
					request(base.resolve("/api/messages"), endpoint).header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
						.build(),
					HttpResponse.BodyHandlers.ofString());
			if (posted.statusCode() == 401) {
				throw new Unauthorized();
			}
			if (posted.statusCode() != 202) {
				return new Answer(Answer.Kind.REJECTED, problemDetail(posted.body()));
			}
			String taskId = taskId(posted.body());
			return awaitWithTimeout(lines.iterator(), taskId, lines);
		}
	}

	private Answer awaitWithTimeout(Iterator<String> lines, String taskId, Stream<String> stream)
			throws IOException, InterruptedException {
		ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
		try {
			Future<Answer> answer = reader.submit(() -> awaitAnswer(lines, taskId));
			return answer.get(answerTimeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			stream.close();
			throw new IOException("No answer from the daemon within " + answerTimeout.toSeconds() + " seconds");
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof IOException io) {
				throw io;
			}
			throw new IOException("Reading the answer failed: " + ex.getCause().getMessage(), ex.getCause());
		}
		finally {
			reader.shutdownNow();
		}
	}

	/**
	 * Reads SSE frames until a {@code reply} or {@code error} for {@code taskId}. Other tasks' frames, unknown event
	 * names (later progress events) and malformed frames are skipped.
	 */
	static Answer awaitAnswer(Iterator<String> lines, String taskId) throws IOException {
		String event = null;
		StringBuilder data = new StringBuilder();
		while (lines.hasNext()) {
			String line = lines.next();
			if (line.isEmpty()) {
				Answer answer = event == null || data.isEmpty() ? null : answerFor(event, data.toString(), taskId);
				if (answer != null) {
					return answer;
				}
				event = null;
				data.setLength(0);
			}
			else if (line.startsWith("event:")) {
				event = line.substring("event:".length()).strip();
			}
			else if (line.startsWith("data:")) {
				if (!data.isEmpty()) {
					data.append('\n');
				}
				data.append(line.substring("data:".length()).stripLeading());
			}
		}
		throw new IOException("The connection to the daemon closed before the answer arrived");
	}

	private static Answer answerFor(String event, String data, String taskId) {
		Answer.Kind kind = switch (event) {
			case "reply" -> Answer.Kind.REPLY;
			case "error" -> Answer.Kind.ERROR;
			default -> null;
		};
		if (kind == null) {
			return null;
		}
		try {
			JsonNode payload = JSON.readTree(data);
			return taskId.equals(payload.path("taskId").asString()) ? new Answer(kind, payload.path("text").asString())
					: null;
		}
		catch (JacksonException ex) {
			return null;
		}
	}

	private static String taskId(String body) throws IOException {
		try {
			String taskId = JSON.readTree(body).path("taskId").asString("");
			if (taskId.isEmpty()) {
				throw new IOException("The daemon accepted the message but returned no taskId");
			}
			return taskId;
		}
		catch (JacksonException ex) {
			throw new IOException("The daemon returned an unreadable response: " + ex.getOriginalMessage());
		}
	}

	private static String problemDetail(String body) {
		try {
			JsonNode problem = JSON.readTree(body);
			return problem.path("detail").asString(body);
		}
		catch (JacksonException ex) {
			return body;
		}
	}

	private static HttpRequest.Builder request(URI uri, DaemonEndpoint endpoint) {
		return HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + endpoint.token());
	}

	private static final class Unauthorized extends Exception {

	}

	private static final class Unreachable extends Exception {

	}

}
