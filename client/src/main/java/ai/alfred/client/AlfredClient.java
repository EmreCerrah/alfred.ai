package ai.alfred.client;

import java.io.BufferedReader;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * {@code alfred} with arguments sends them as one message; without arguments it reads one message per line until EOF.
 * Exit codes: 0 reply, 1 Alfred reported a failure or refused the request, 2 the daemon could not be reached.
 */
public final class AlfredClient {

	static final int REPLIED = 0;

	static final int FAILED = 1;

	static final int UNREACHABLE = 2;

	private AlfredClient() {
	}

	public static void main(String[] args) throws Exception {
		DaemonClient client = new DaemonClient(
				DaemonEndpoint.runtimeFile(System.getenv(), System.getProperty("user.home")));
		// Since JDK 22 System.console() exists even when redirected; only a real terminal gets its charset and the
		// prompt. Pipes and terminal emulators (Git Bash, Sway scripts) get UTF-8, so Turkish text is not garbled.
		Console console = System.console();
		boolean terminal = console != null && console.isTerminal();
		Charset charset = terminal ? console.charset() : StandardCharsets.UTF_8;
		PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, charset);
		PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, charset);
		if (args.length > 0) {
			System.exit(ask(client, String.join(" ", args), out, err));
		}
		System.exit(repl(client, new BufferedReader(new InputStreamReader(System.in, charset)), out, err, terminal));
	}

	/** One message per line; stops at EOF, or as soon as the daemon cannot be reached. Returns the last exit code. */
	static int repl(DaemonClient client, BufferedReader in, PrintStream out, PrintStream err, boolean prompt)
			throws IOException, InterruptedException {
		int last = REPLIED;
		while (true) {
			if (prompt) {
				out.print("> ");
				out.flush();
			}
			String line = in.readLine();
			if (line == null) {
				return last;
			}
			if (!line.isBlank()) {
				last = ask(client, line, out, err);
				if (last == UNREACHABLE) {
					return last;
				}
			}
		}
	}

	static int ask(DaemonClient client, String text, PrintStream out, PrintStream err) throws InterruptedException {
		try {
			DaemonClient.Answer answer = client.send(text);
			if (answer.kind() == DaemonClient.Answer.Kind.REJECTED) {
				err.println(answer.text());
				return FAILED;
			}
			out.println(answer.text());
			return answer.kind() == DaemonClient.Answer.Kind.REPLY ? REPLIED : FAILED;
		}
		catch (DaemonNotRunning ex) {
			err.println(ex.getMessage());
			return UNREACHABLE;
		}
		catch (IOException | RuntimeException ex) {
			err.println("alfred: " + ex.getMessage());
			return UNREACHABLE;
		}
	}

}
