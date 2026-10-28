package ai.alfred.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the thin client against a stand-in daemon on a random local port. Like the real one, the stub keeps no
 * backlog: a reply goes only to subscriptions that are open when the message is posted.
 */
class DaemonClientTests {

	@TempDir
	Path runtimeDir;

	HttpServer server;

	volatile String validToken = "token-1";

	/** Set to simulate a restart: the first rejected request rewrites the runtime file with this token. */
	volatile String tokenAfter401;

	volatile int messageStatus = 202;

	volatile String replyEvent = "reply";

	volatile boolean answer = true;

	final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

	record Subscriber(OutputStream out, CountDownLatch done) {
	}

	@BeforeEach
	void startStub() throws IOException {
		server = startServer();
		writeRuntimeFile(server.getAddress().getPort(), "token-1");
	}

	@AfterEach
	void stopStub() {
		subscribers.forEach(subscriber -> subscriber.done().countDown());
		server.stop(0);
	}

	@Test
	void sendsTheMessageAndReturnsTheReplyForItsTask() throws Exception {
		assertThat(client().send("Merhaba alfred")).isEqualTo(
				new DaemonClient.Answer(DaemonClient.Answer.Kind.REPLY, "Buyurun, efendim.\nIkinci satir"));
	}

	@Test
	void returnsAlfredsExplanationForAFailedTask() throws Exception {
		replyEvent = "error";

		assertThat(client().send("Merhaba").kind()).isEqualTo(DaemonClient.Answer.Kind.ERROR);
	}

	@Test
	void rereadsTheRuntimeFileOnceWhenTheDaemonRestartedWithANewToken() throws Exception {
		validToken = "token-2";
		tokenAfter401 = "token-2";

		assertThat(client().send("Merhaba").kind()).isEqualTo(DaemonClient.Answer.Kind.REPLY);
	}

	@Test
	void givesUpWhenTheReReadTokenIsRejectedToo() {
		validToken = "token-other";

		assertThatExceptionOfType(DaemonNotRunning.class).isThrownBy(() -> client().send("Merhaba"))
			.withMessageContaining("rejected the token");
	}

	@Test
	void followsTheDaemonToItsNewPortAfterARestart() throws Exception {
		// First read: the old port, where nothing listens any more. Second read: the restarted daemon.
		java.util.Iterator<DaemonEndpoint> reads = List
			.of(new DaemonEndpoint(freePort(), "token-1"), new DaemonEndpoint(server.getAddress().getPort(), "token-1"))
			.iterator();
		DaemonClient client = new DaemonClient(reads::next, "daemon.json", Duration.ofSeconds(10));

		assertThat(client.send("Merhaba").kind()).isEqualTo(DaemonClient.Answer.Kind.REPLY);
	}

	@Test
	void reportsARejectedRequestWithTheProblemDetail() throws Exception {
		messageStatus = 409;

		assertThat(client().send("Merhaba"))
			.isEqualTo(new DaemonClient.Answer(DaemonClient.Answer.Kind.REJECTED, "Efendim, mesgulum."));
	}

	@Test
	void saysTheDaemonIsNotRunningWhenTheRuntimeFileIsMissing() throws IOException {
		Files.delete(runtimeDir.resolve("daemon.json"));

		assertThatExceptionOfType(DaemonNotRunning.class).isThrownBy(() -> client().send("Merhaba"))
			.withMessageContaining("not running");
	}

	@Test
	void saysTheDaemonIsNotRunningWhenNothingListens() throws IOException {
		writeRuntimeFile(freePort(), "token-1");

		assertThatExceptionOfType(DaemonNotRunning.class).isThrownBy(() -> client().send("Merhaba"))
			.withMessageContaining("nothing listens");
	}

	@Test
	void stopsWaitingWhenTheDaemonNeverAnswers() {
		answer = false;
		DaemonClient client = new DaemonClient(runtimeDir.resolve("daemon.json"), Duration.ofMillis(300));

		assertThatExceptionOfType(IOException.class).isThrownBy(() -> client.send("Merhaba"))
			.withMessageContaining("No answer");
	}

	@Test
	void skipsOtherTasksUnknownEventsAndMalformedFramesAndJoinsMultiLineData() throws IOException {
		List<String> stream = List.of(": connected", "", "event: reply", "data: {\"taskId\": \"other\", \"text\": \"x\"}",
				"", "event: progress", "data: {\"taskId\": \"mine\", \"text\": \"working\"}", "", "event: reply",
				"data: not json", "", "event: reply", "data: {\"taskId\": \"mine\",", "data:  \"text\": \"y\"}", "");

		assertThat(DaemonClient.awaitAnswer(stream.iterator(), "mine"))
			.isEqualTo(new DaemonClient.Answer(DaemonClient.Answer.Kind.REPLY, "y"));
	}

	@Test
	void failsWhenTheStreamEndsBeforeTheAnswer() {
		assertThatExceptionOfType(IOException.class)
			.isThrownBy(() -> DaemonClient.awaitAnswer(List.of(": connected", "").iterator(), "mine"));
	}

	@Test
	void mapsAnswersToExitCodesAndStreams() throws Exception {
		Streams reply = new Streams();
		assertThat(AlfredClient.ask(client(), "Merhaba", reply.out, reply.err)).isEqualTo(AlfredClient.REPLIED);
		assertThat(reply.out()).contains("Buyurun, efendim.");
		assertThat(reply.err()).isEmpty();

		replyEvent = "error";
		Streams error = new Streams();
		assertThat(AlfredClient.ask(client(), "Merhaba", error.out, error.err)).isEqualTo(AlfredClient.FAILED);
		assertThat(error.out()).contains("Buyurun, efendim.");

		replyEvent = "reply";
		messageStatus = 409;
		Streams rejected = new Streams();
		assertThat(AlfredClient.ask(client(), "Merhaba", rejected.out, rejected.err)).isEqualTo(AlfredClient.FAILED);
		assertThat(rejected.out()).isEmpty();
		assertThat(rejected.err()).contains("mesgulum");

		Files.delete(runtimeDir.resolve("daemon.json"));
		Streams down = new Streams();
		assertThat(AlfredClient.ask(client(), "Merhaba", down.out, down.err)).isEqualTo(AlfredClient.UNREACHABLE);
		assertThat(down.err()).contains("not running");
	}

	@Test
	void replPrintsNoPromptForPipedInputAndStopsWhenTheDaemonIsGone() throws Exception {
		Streams piped = new Streams();
		int code = AlfredClient.repl(client(), new BufferedReader(new StringReader("Merhaba\n\nNasilsin\n")), piped.out,
				piped.err, false);

		assertThat(code).isEqualTo(AlfredClient.REPLIED);
		assertThat(piped.out()).doesNotContain("> ").contains("Buyurun, efendim.");

		Files.delete(runtimeDir.resolve("daemon.json"));
		Streams gone = new Streams();
		assertThat(AlfredClient.repl(client(), new BufferedReader(new StringReader("Bir\nIki\n")), gone.out, gone.err,
				true))
			.isEqualTo(AlfredClient.UNREACHABLE);
		assertThat(gone.err().lines().filter(line -> line.contains("not running"))).hasSize(1);
	}

	@Test
	void resolvesTheSameRuntimeDirectoryAsTheDaemon() {
		assertThat(DaemonEndpoint.runtimeDir(Map.of("ALFRED_RUNTIME_DIR", "/x", "XDG_RUNTIME_DIR", "/run/user/1000"),
				"/home/e"))
			.isEqualTo(Path.of("/x"));
		assertThat(DaemonEndpoint.runtimeDir(Map.of("XDG_RUNTIME_DIR", "/run/user/1000"), "/home/e"))
			.isEqualTo(Path.of("/run/user/1000", "alfred"));
		assertThat(DaemonEndpoint.runtimeDir(Map.of("LOCALAPPDATA", "C:/Users/e/AppData/Local"), "/home/e"))
			.isEqualTo(Path.of("C:/Users/e/AppData/Local", "alfred"));
		assertThat(DaemonEndpoint.runtimeDir(Map.of(), "/home/e"))
			.isEqualTo(Path.of("/home/e", ".local", "state", "alfred"));
	}

	@Test
	void neverPrintsTheToken() {
		assertThat(new DaemonEndpoint(1, "secret-token")).hasToString("DaemonEndpoint[port=1, token=***]");
	}

	static final class Streams {

		final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

		final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

		final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

		final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

		String out() {
			return outBytes.toString(StandardCharsets.UTF_8);
		}

		String err() {
			return errBytes.toString(StandardCharsets.UTF_8);
		}

	}

	private DaemonClient client() {
		return new DaemonClient(runtimeDir.resolve("daemon.json"), Duration.ofSeconds(10));
	}

	private HttpServer startServer() throws IOException {
		HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		stub.setExecutor(Executors.newCachedThreadPool());
		stub.createContext("/api/events", this::events);
		stub.createContext("/api/messages", this::messages);
		stub.start();
		return stub;
	}

	private static int freePort() throws IOException {
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private void writeRuntimeFile(int port, String token) throws IOException {
		Files.writeString(runtimeDir.resolve("daemon.json"), "{\"port\": %d, \"token\": \"%s\"}".formatted(port, token));
	}

	private boolean authorized(HttpExchange exchange) throws IOException {
		if (("Bearer " + validToken).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
			return true;
		}
		if (tokenAfter401 != null) {
			writeRuntimeFile(exchange.getLocalAddress().getPort(), tokenAfter401);
			tokenAfter401 = null;
		}
		exchange.sendResponseHeaders(401, -1);
		exchange.close();
		return false;
	}

	private void events(HttpExchange exchange) throws IOException {
		if (!authorized(exchange)) {
			return;
		}
		exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
		exchange.sendResponseHeaders(200, 0);
		OutputStream out = exchange.getResponseBody();
		write(out, ": connected\n\n");
		Subscriber subscriber = new Subscriber(out, new CountDownLatch(1));
		subscribers.add(subscriber);
		try {
			subscriber.done().await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		finally {
			subscribers.remove(subscriber);
			exchange.close();
		}
	}

	private void messages(HttpExchange exchange) throws IOException {
		if (!authorized(exchange)) {
			return;
		}
		exchange.getRequestBody().readAllBytes();
		byte[] body;
		String taskId = "task-" + System.nanoTime();
		if (messageStatus == 202) {
			body = "{\"taskId\": \"%s\"}".formatted(taskId).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
		}
		else {
			body = "{\"status\": %d, \"detail\": \"Efendim, mesgulum.\"}".formatted(messageStatus)
				.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
		}
		exchange.sendResponseHeaders(messageStatus, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
		if (messageStatus == 202 && answer) {
			// Only to those already listening, exactly like the daemon's EventStream.
			for (Subscriber subscriber : subscribers) {
				write(subscriber.out(), "event: reply\ndata: {\"taskId\": \"someone-else\", \"text\": \"not for us\"}\n\n");
				write(subscriber.out(), "event: %s\ndata: {\"taskId\": \"%s\", \"text\": \"Buyurun, efendim.\\nIkinci satir\"}\n\n"
					.formatted(replyEvent, taskId));
				subscriber.done().countDown();
			}
		}
	}

	private static void write(OutputStream out, String text) throws IOException {
		out.write(text.getBytes(StandardCharsets.UTF_8));
		out.flush();
	}

}
