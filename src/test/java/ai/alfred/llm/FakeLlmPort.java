package ai.alfred.llm;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A scripted {@link LlmPort} for tests of other modules: answers with a fixed text, throws, or blocks until released.
 * Lives in {@code ai.alfred.llm} because only this package can create an assistant turn.
 */
public class FakeLlmPort implements LlmPort {

	private final AtomicReference<Behavior> behavior = new AtomicReference<>(new Behavior("Buyurun, efendim.", null));

	private volatile CountDownLatch gate = new CountDownLatch(0);

	private volatile LlmRequest lastRequest;

	private record Behavior(String reply, RuntimeException failure) {
	}

	public FakeLlmPort replyWith(String text) {
		behavior.set(new Behavior(text, null));
		return this;
	}

	public FakeLlmPort failWith(RuntimeException failure) {
		behavior.set(new Behavior(null, failure));
		return this;
	}

	/** Every call waits until {@link #release()}, so a Task can be held active. */
	public FakeLlmPort hold() {
		gate = new CountDownLatch(1);
		return this;
	}

	public void release() {
		gate.countDown();
	}

	/** Back to the default reply, no hold, no recorded request; for contexts shared between tests. */
	public void reset() {
		gate.countDown();
		lastRequest = null;
		replyWith("Buyurun, efendim.");
	}

	public LlmRequest lastRequest() {
		return lastRequest;
	}

	public static LlmUnavailable unavailable() {
		return new LlmUnavailable("fake: Claude unreachable");
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		lastRequest = request;
		try {
			if (!gate.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("FakeLlmPort was never released");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
		Behavior current = behavior.get();
		if (current.failure() != null) {
			throw current.failure();
		}
		return new LlmResponse(new LlmMessage.Assistant(current.reply(), List.of(), new ProviderContent(List.of())),
				StopReason.END_TURN, new Usage(1, 1, 0, 0));
	}

}
