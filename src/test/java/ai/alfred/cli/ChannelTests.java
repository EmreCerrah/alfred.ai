package ai.alfred.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import ai.alfred.TestcontainersConfiguration;
import ai.alfred.llm.FakeLlmPort;
import ai.alfred.llm.LlmPort;
import ai.alfred.persona.Persona;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CLI channel over real HTTP on a random port, with a scripted {@link LlmPort}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "ALFRED_DB_PASSWORD=unused-by-service-connection")
@Import({ TestcontainersConfiguration.class, ChannelTests.FakeLlm.class })
class ChannelTests {

	static final Path RUNTIME_DIR = createRuntimeDir();

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeLlm {

		@Bean
		@Primary
		FakeLlmPort fakeLlmPort() {
			return new FakeLlmPort();
		}

	}

	@DynamicPropertySource
	static void runtimeDir(DynamicPropertyRegistry registry) {
		registry.add("alfred.cli.runtime-dir", RUNTIME_DIR::toString);
	}

	final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	final JsonMapper json = JsonMapper.builder().build();

	final Persona persona = Persona.load("tr");

	@LocalServerPort
	int port;

	@Autowired
	DaemonToken token;

	@Autowired
	FakeLlmPort llm;

	@Autowired
	EventStream events;

	@Autowired
	ServletWebServerApplicationContext webServer;

	@BeforeEach
	@AfterEach
	void reset() {
		llm.reset();
	}

	@Test
	void bindsTheConnectorToLoopbackOnly() throws IOException {
		// Tomcat binds exactly this address; a firewall on the external interface cannot hide a wrong bind here.
		Object address = ((TomcatWebServer) webServer.getWebServer()).getTomcat().getConnector().getProperty("address");

		assertThat(address).isInstanceOfSatisfying(InetAddress.class,
				bound -> assertThat(bound.isLoopbackAddress()).isTrue());
		try (Socket loopback = new Socket()) {
			loopback.connect(new InetSocketAddress("127.0.0.1", port), 2000);
		}
	}

	@Test
	void writesTheRuntimeFileForTheThinClient() throws IOException {
		Path file = RUNTIME_DIR.resolve("daemon.json");
		JsonNode content = json.readTree(Files.readString(file));

		assertThat(content.get("port").asInt()).isEqualTo(port);
		assertThat(token.matches(content.get("token").asString())).isTrue();
		if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
			assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
			assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(RUNTIME_DIR)))
				.isEqualTo("rwx------");
		}
	}

	@Test
	void rejectsRequestsWithoutAValidTokenWith401() throws Exception {
		HttpResponse<String> missing = http.send(HttpRequest.newBuilder(uri("/api/messages"))
			.POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"hi\"}"))
			.header("Content-Type", "application/json")
			.build(), HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> wrong = http.send(post("{\"text\":\"hi\"}", "Bearer wrong-token"),
				HttpResponse.BodyHandlers.ofString());

		for (HttpResponse<String> response : java.util.List.of(missing, wrong)) {
			assertThat(response.statusCode()).isEqualTo(401);
			assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
					type -> assertThat(type).startsWith("application/problem+json"));
			assertThat(json.readTree(response.body()).get("status").asInt()).isEqualTo(401);
		}
		assertThat(llm.lastRequest()).isNull();
	}

	@Test
	void guardsEveryPathIncludingEncodedAndMatrixVariantsOfTheApi() throws Exception {
		for (String path : java.util.List.of("/api;x=1/messages", "/%61pi/messages", "/error", "/", "/anything")) {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"hi\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).as(path).isEqualTo(401);
		}
		assertThat(llm.lastRequest()).isNull();
	}

	@Test
	void rejectsBrowserOriginsOnTheEventStreamToo() throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/events"))
			.header("Authorization", bearer())
			.header("Origin", "https://evil.example")
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(403);
	}

	@Test
	void rejectsBrowserOriginsWith403() throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/messages"))
			.header("Authorization", "Bearer " + token.value())
			.header("Origin", "https://evil.example")
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"hi\"}"))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(json.readTree(response.body()).get("status").asInt()).isEqualTo(403);
	}

	@Test
	void rejectsForeignHostHeadersWith403() throws IOException {
		// HttpClient will not let us set Host, and DNS rebinding is exactly a foreign Host on a loopback socket.
		String response = rawRequest("""
				POST /api/messages HTTP/1.1\r
				Host: evil.example:%d\r
				Authorization: Bearer %s\r
				Content-Type: application/json\r
				Content-Length: 13\r
				Connection: close\r
				\r
				{"text":"hi"}""".formatted(port, token.value()));

		assertThat(response).startsWith("HTTP/1.1 403");
	}

	@Test
	void repliesToAMessageOverServerSentEventsWithTheSameTaskId() throws Exception {
		llm.replyWith("Buyurun, efendim. Bugun nasil yardimci olabilirim?");

		try (Stream<String> events = subscribe()) {
			HttpResponse<String> posted = http.send(post("{\"text\":\"Merhaba alfred\"}", bearer()),
					HttpResponse.BodyHandlers.ofString());

			assertThat(posted.statusCode()).isEqualTo(202);
			String taskId = json.readTree(posted.body()).get("taskId").asString();
			JsonNode reply = awaitEvent(events.iterator(), "reply", taskId);
			assertThat(reply.get("text").asString()).isEqualTo("Buyurun, efendim. Bugun nasil yardimci olabilirim?");
		}
	}

	@Test
	void sendsAnErrorEventWhenClaudeIsUnavailable() throws Exception {
		llm.failWith(FakeLlmPort.unavailable());

		try (Stream<String> events = subscribe()) {
			String taskId = json.readTree(http.send(post("{\"text\":\"Merhaba\"}", bearer()),
					HttpResponse.BodyHandlers.ofString()).body()).get("taskId").asString();

			JsonNode error = awaitEvent(events.iterator(), "error", taskId);
			assertThat(error.get("text").asString()).isEqualTo(persona.text("llm.unavailable"));
		}
	}

	@Test
	void refusesASecondRequestWhileATaskIsActiveWith409AndDoesNotQueueIt() throws Exception {
		llm.hold();
		try (Stream<String> events = subscribe()) {
			Iterator<String> lines = events.iterator();
			String first = json.readTree(http.send(post("{\"text\":\"Bir\"}", bearer()),
					HttpResponse.BodyHandlers.ofString()).body()).get("taskId").asString();

			HttpResponse<String> second = http.send(post("{\"text\":\"Iki\"}", bearer()),
					HttpResponse.BodyHandlers.ofString());
			assertThat(second.statusCode()).isEqualTo(409);
			assertThat(json.readTree(second.body()).get("detail").asString()).isEqualTo(persona.text("agent.busy"));

			llm.release();
			awaitEvent(lines, "reply", first);
			// The finished Task left the registry before its reply was sent, so the next request is accepted at once.
			assertThat(http.send(post("{\"text\":\"Uc\"}", bearer()), HttpResponse.BodyHandlers.ofString())
				.statusCode()).isEqualTo(202);
		}
	}

	@Test
	void rejectsBlankTooLongAndMalformedMessagesWith400ProblemDetails() throws Exception {
		HttpResponse<String> blank = http.send(post("{\"text\":\"  \"}", bearer()),
				HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> tooLong = http.send(post("{\"text\":\"" + "x".repeat(20001) + "\"}", bearer()),
				HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> malformed = http.send(post("{not json", bearer()), HttpResponse.BodyHandlers.ofString());

		assertThat(blank.statusCode()).isEqualTo(400);
		assertThat(json.readTree(blank.body()).get("detail").asString()).isEqualTo(persona.text("agent.message-blank"));
		assertThat(tooLong.statusCode()).isEqualTo(400);
		assertThat(json.readTree(tooLong.body()).get("detail").asString())
			.isEqualTo(persona.text("agent.message-too-long", 20000));
		assertThat(malformed.statusCode()).isEqualTo(400);
		assertThat(malformed.headers().firstValue("Content-Type"))
			.hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
		assertThat(llm.lastRequest()).isNull();
	}

	@Test
	void dropsSubscribersThatHaveGoneAway() throws Exception {
		int before = events.subscribers();
		subscribe().close();

		// Tomcat notices a closed client only when it next writes to it, so keep replying until the emitter is gone.
		for (int i = 0; i < 50 && events.subscribers() > before; i++) {
			http.send(post("{\"text\":\"ping\"}", bearer()), HttpResponse.BodyHandlers.ofString());
			TimeUnit.MILLISECONDS.sleep(50);
		}
		assertThat(events.subscribers()).isEqualTo(before);
	}

	private Stream<String> subscribe() throws Exception {
		HttpResponse<Stream<String>> response = http.send(HttpRequest.newBuilder(uri("/api/events"))
			.header("Authorization", bearer())
			.header("Accept", "text/event-stream")
			.build(), HttpResponse.BodyHandlers.ofLines());
		assertThat(response.statusCode()).isEqualTo(200);
		return response.body();
	}

	private JsonNode awaitEvent(Iterator<String> lines, String name, String taskId) {
		String event = null;
		String data = null;
		while (lines.hasNext()) {
			String line = lines.next();
			if (line.startsWith("event:")) {
				event = line.substring(6).strip();
			}
			else if (line.startsWith("data:")) {
				data = line.substring(5).strip();
			}
			else if (line.isEmpty() && data != null) {
				JsonNode payload = json.readTree(data);
				if (name.equals(event) && taskId.equals(payload.get("taskId").asString())) {
					return payload;
				}
				event = null;
				data = null;
			}
		}
		throw new AssertionError("stream ended before a '" + name + "' event for " + taskId);
	}

	private HttpRequest post(String body, String authorization) {
		return HttpRequest.newBuilder(uri("/api/messages"))
			.header("Authorization", authorization)
			.header("Content-Type", "application/json")
			.timeout(Duration.ofSeconds(10))
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
	}

	private String bearer() {
		return "Bearer " + token.value();
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

	private String rawRequest(String request) throws IOException {
		try (Socket socket = new Socket("127.0.0.1", port)) {
			OutputStream out = socket.getOutputStream();
			out.write(request.getBytes(StandardCharsets.UTF_8));
			out.flush();
			return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
				.readLine();
		}
	}

	private static Path createRuntimeDir() {
		try {
			return Files.createTempDirectory("alfred-runtime-");
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
