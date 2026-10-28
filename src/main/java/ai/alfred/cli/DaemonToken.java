package ai.alfred.cli;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * The per-start secret every request must carry (AD-10). Never logged; only written to the runtime file.
 */
@Component
class DaemonToken {

	private static final int BYTES = 32;

	private final String value;

	DaemonToken() {
		byte[] bytes = new byte[BYTES];
		new SecureRandom().nextBytes(bytes);
		this.value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	String value() {
		return value;
	}

	/** Constant-time, so response timing reveals nothing about how much of a guess was right. */
	boolean matches(String candidate) {
		return candidate != null && MessageDigest.isEqual(value.getBytes(StandardCharsets.US_ASCII),
				candidate.getBytes(StandardCharsets.US_ASCII));
	}

	@Override
	public String toString() {
		return "DaemonToken[***]";
	}

}
