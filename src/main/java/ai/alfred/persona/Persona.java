package ai.alfred.persona;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Alfred's voice: the system prompt and every fixed user-facing text, loaded from {@code persona/<language>/} on the
 * classpath (AD-18). Other modules ask by key; no Turkish lives in Java code.
 */
@Component
public class Persona {

	/** Whitespace and full stops or exclamation marks a command may end with. */
	private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s.!]+$");

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

	/**
	 * Whether {@code input} is the command whose accepted forms are the text for {@code key}, separated by {@code |}.
	 * The match is exact after ignoring leading and trailing whitespace, trailing {@code .} or {@code !} and case (by
	 * this persona's language rules); anything else, such as a longer sentence, is not the command.
	 * @throws IllegalArgumentException for an unknown key
	 */
	public boolean isCommand(String key, String input) {
		if (input == null) {
			return false;
		}
		// The persona's rules fold "YENİ" to "yeni"; ROOT folds ASCII "YENI" to "yeni" where Turkish would give "yenı".
		String byLanguage = normalize(input, locale);
		String byRoot = normalize(input, Locale.ROOT);
		return Arrays.stream(text(key).split("\\|"))
			.map(form -> normalize(form, locale))
			.anyMatch(form -> form.equals(byLanguage) || form.equals(byRoot));
	}

	private static String normalize(String text, Locale caseRules) {
		String stripped = TRAILING_PUNCTUATION.matcher(text.strip()).replaceFirst("");
		return Normalizer.normalize(stripped, Normalizer.Form.NFC).toLowerCase(caseRules);
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
