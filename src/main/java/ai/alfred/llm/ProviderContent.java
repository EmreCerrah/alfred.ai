package ai.alfred.llm;

import java.util.List;

/**
 * Opaque, provider-owned content of an assistant turn. Callers keep it and hand it back; only the {@code llm} module can
 * read it. It lives in memory only, like the Conversation that holds it (AD-16).
 */
public final class ProviderContent {

	private final List<?> blocks;

	ProviderContent(List<?> blocks) {
		this.blocks = List.copyOf(blocks);
	}

	List<?> blocks() {
		return blocks;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ProviderContent content && blocks.equals(content.blocks);
	}

	@Override
	public int hashCode() {
		return blocks.hashCode();
	}

}
