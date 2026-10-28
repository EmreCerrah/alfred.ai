package ai.alfred.persona;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Alfred's voice: the system prompt and every fixed user-facing text, loaded from {@code persona/<language>/} on the
 * classpath (AD-18). Other modules ask by key; no Turkish lives in Java code.
 */
@Component
public class Persona {

	private final String systemPrompt;

	private final Properties texts;

	private final Locale locale;

	@Autowired
	Persona(PersonaProperties properties) {
		this(properties.language());
	}

	private Persona(String language) {
		String base = "persona/" + language + "/";
		this.locale = Locale.forLanguageTag(language);
		this.systemPrompt = read(base + "system-prompt.md").strip();
		this.texts = new Properties();
		try (Reader reader = new InputStreamReader(open(base + "messages.properties"), StandardCharsets.UTF_8)) {
			texts.load(reader);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Loads the persona for {@code language} outside Spring, e.g. in tests of other modules.
	 */
	public static Persona load(String language) {
		return new Persona(language);
	}

	/**
	 * The system prompt; identical for the whole life of the daemon, so the prompt cache and preserved thinking hold.
	 */
	public String systemPrompt() {
		return systemPrompt;
	}

	/**
	 * The fixed text for {@code key}, with {@link MessageFormat} arguments.
	 * @throws IllegalArgumentException for an unknown key, so a missing text fails loudly
	 */
	public String text(String key, Object... args) {
		String pattern = texts.getProperty(key);
		if (pattern == null) {
			throw new IllegalArgumentException("No persona text for key '" + key + "'");
		}
		return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
	}

	private static String read(String path) {
		try (InputStream in = open(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static InputStream open(String path) {
		try {
			return new ClassPathResource(path).getInputStream();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Missing persona resource: " + path, ex);
		}
	}

}
