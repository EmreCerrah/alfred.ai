package ai.alfred.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * The one shared Conversation (AD-9): an append-only, in-memory history (AD-11, AD-16). Entries are never trimmed,
 * edited or reordered; only {@link #reset()} ("new conversation") or a daemon restart ends it. There is no clock, so an
 * idle Conversation never expires. Generic over the entry type because {@code memory} depends on no other module.
 * <p>
 * Thread-safe: every method holds this object's lock, so an {@link #append(List) append} is all-or-nothing.
 */
public class Conversation<E> {

	private final List<E> entries = new ArrayList<>();

	private final long suggestResetTokens;

	private boolean resetSuggested;

	/**
	 * @param suggestResetTokens prompt size above which a reset is suggested, once per Conversation
	 */
	public Conversation(long suggestResetTokens) {
		if (suggestResetTokens < 1) {
			throw new IllegalArgumentException("suggestResetTokens must be positive");
		}
		this.suggestResetTokens = suggestResetTokens;
	}

	/** A snapshot of the history, oldest first. */
	public synchronized List<E> messages() {
		return List.copyOf(entries);
	}

	/** Appends the entries at the end, all together or not at all. */
	@SafeVarargs
	public final void append(E... entries) {
		append(List.of(entries));
	}

	/** Appends the entries at the end, all together or not at all. */
	public synchronized void append(List<? extends E> newEntries) {
		List<E> checked = List.copyOf(newEntries);
		entries.addAll(checked);
	}

	/** Ends the Conversation: the history is empty and the reset suggestion may be given again. */
	public synchronized void reset() {
		entries.clear();
		resetSuggested = false;
	}

	/**
	 * Whether to suggest a reset now: true exactly once per Conversation, for the first call whose prompt exceeded
	 * the threshold.
	 */
	public synchronized boolean claimResetSuggestion(long promptTokens) {
		if (resetSuggested || promptTokens <= suggestResetTokens) {
			return false;
		}
		resetSuggested = true;
		return true;
	}

}
