package ai.alfred.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class ConversationTests {

	final Conversation<String> conversation = new Conversation<>(100);

	@Test
	void startsEmptyAndAppendsAtTheEndInOrder() {
		assertThat(conversation.messages()).isEmpty();

		conversation.append("user-1", "assistant-1");
		conversation.append(List.of("user-2", "assistant-2"));

		assertThat(conversation.messages()).containsExactly("user-1", "assistant-1", "user-2", "assistant-2");
	}

	@Test
	void keepsTheSameEntryObjects() {
		StringBuilder entry = new StringBuilder("assistant");
		Conversation<Object> objects = new Conversation<>(100);

		objects.append(entry);

		assertThat(objects.messages().getFirst()).isSameAs(entry);
	}

	@Test
	void handsOutSnapshotsThatCannotChangeTheHistory() {
		conversation.append("a");
		List<String> snapshot = conversation.messages();

		conversation.append("b");

		assertThat(snapshot).containsExactly("a");
		assertThat(conversation.messages()).containsExactly("a", "b");
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> snapshot.add("x")))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void appendsAllOrNothing() {
		conversation.append("a");

		assertThatNullPointerException().isThrownBy(() -> conversation.append(Arrays.asList("b", null)));

		assertThat(conversation.messages()).containsExactly("a");
	}

	@Test
	void keepsConcurrentPairsTogether() throws Exception {
		int writers = 8;
		int pairs = 200;
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int w = 0; w < writers; w++) {
				int writer = w;
				pool.execute(() -> {
					try {
						start.await();
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						return;
					}
					for (int i = 0; i < pairs; i++) {
						conversation.append("q" + writer + "-" + i, "a" + writer + "-" + i);
					}
				});
			}
			start.countDown();
		}

		List<String> history = conversation.messages();
		assertThat(history).hasSize(writers * pairs * 2);
		for (int i = 0; i < history.size(); i += 2) {
			assertThat(history.get(i + 1)).isEqualTo("a" + history.get(i).substring(1));
		}
	}

	@Test
	void resetEmptiesTheHistory() {
		conversation.append("a", "b");

		conversation.reset();

		assertThat(conversation.messages()).isEmpty();
		conversation.append("c");
		assertThat(conversation.messages()).containsExactly("c");
	}

	@Test
	void suggestsAResetOnceAboveTheThreshold() {
		assertThat(conversation.claimResetSuggestion(50)).isFalse();
		assertThat(conversation.claimResetSuggestion(100)).as("at the threshold").isFalse();
		assertThat(conversation.claimResetSuggestion(101)).isTrue();
		assertThat(conversation.claimResetSuggestion(5000)).isFalse();
		assertThat(conversation.claimResetSuggestion(101)).isFalse();
	}

	@Test
	void suggestsAgainAfterAReset() {
		assertThat(conversation.claimResetSuggestion(101)).isTrue();

		conversation.reset();

		assertThat(conversation.claimResetSuggestion(101)).isTrue();
	}

	@Test
	void givesTheSuggestionToExactlyOneOfManyConcurrentCallers() throws Exception {
		List<Boolean> claims = java.util.Collections.synchronizedList(new ArrayList<>());
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < 50; i++) {
				pool.execute(() -> claims.add(conversation.claimResetSuggestion(1000)));
			}
		}

		assertThat(claims).hasSize(50).containsOnlyOnce(true);
	}

	@Test
	void rejectsANonPositiveThreshold() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Conversation<String>(0));
		assertThatIllegalArgumentException().isThrownBy(() -> new MemoryProperties(-1));
		assertThat(new MemoryProperties(80_000).suggestResetTokens()).isEqualTo(80_000);
	}

}
