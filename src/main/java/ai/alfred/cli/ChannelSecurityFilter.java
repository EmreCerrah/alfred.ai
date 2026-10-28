package ai.alfred.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards every request on the connector (AD-10): first the per-start token ({@code 401}), then {@code Host} and
 * {@code Origin} ({@code 403}). Browsers send {@code Origin} on cross-site requests, and a DNS-rebinding page carries
 * its own host name, so both are refused; no web UI is allowed yet. There is deliberately no path exemption: a prefix
 * check on the raw URI could be bypassed with {@code /api;x=1/...} or {@code /%61pi/...}.
 */
class ChannelSecurityFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");

	private final DaemonToken token;

	ChannelSecurityFilter(DaemonToken token) {
		this.token = token;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith(BEARER)
				|| !token.matches(authorization.substring(BEARER.length()))) {
			reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
					"Missing or invalid daemon token.");
			return;
		}
		if (!isLoopback(request.getHeader(HttpHeaders.HOST))) {
			reject(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Host must be a loopback address.");
			return;
		}
		if (request.getHeader(HttpHeaders.ORIGIN) != null) {
			reject(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Browser origins are not accepted.");
			return;
		}
		chain.doFilter(request, response);
	}

	static boolean isLoopback(String host) {
		if (host == null) {
			return false;
		}
		String name = host;
		int portSeparator = host.lastIndexOf(':');
		if (portSeparator > host.lastIndexOf(']')) {
			name = host.substring(0, portSeparator);
		}
		return LOOPBACK_HOSTS.contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	/** RFC 9457 body; only constant strings, so no escaping is needed. */
	private static void reject(HttpServletResponse response, int status, String title, String detail)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter()
			.write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(title,
					status, detail));
	}

}
