package ai.alfred.llm;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A scripted {@link LlmPort} for tests of other modules: answers with a fixed text (with a chosen stop reason and
 * usage), throws, or blocks until released. Lives in {@code ai.alfred.llm} because only this package can create an
 * assistant turn.
 */
public class FakeLlmPort implements LlmPort {

	public static final Usage SMALL_USAGE = new Usage(1, 1, 0, 0);

	private final AtomicReference<Behavior> behavior = new AtomicReference<>(
			new Behavior("Buyurun, efendim.", StopReason.END_TURN, SMALL_USAGE, null, false));

	private final List<LlmRequest> requests = new CopyOnWriteArrayList<>();

	private volatile CountDownLatch gate = new CountDownLatch(0);

	private volatile LlmRequest lastRequest;

	private volatile LlmResponse lastResponse;

	private record Behavior(String reply, StopReason stopReason, Usage usage, RuntimeException failure,
			boolean budgetWarning) {
	}

	public FakeLlmPort replyWith(String text) {
		return replyWith(text, StopReason.END_TURN, SMALL_USAGE);
	}

	/** Replies with {@code text}, stopped for {@code stopReason}, reporting {@code usage}. */
	public FakeLlmPort replyWith(String text, StopReason stopReason, Usage usage) {
		behavior.set(new Behavior(text, stopReason, usage, null, false));
		return this;
	}

	/** Replies like the call that pushed the month past the warning ratio. */
	public FakeLlmPort replyWithBudgetWarning(String text) {
		return replyWithBudgetWarning(text, SMALL_USAGE);
	}

	/** Replies like the call that pushed the month past the warning ratio, reporting {@code usage}. */
	public FakeLlmPort replyWithBudgetWarning(String text, Usage usage) {
		return replyWithBudgetWarning(text, StopReason.END_TURN, usage);
	}

	/** Like {@link #replyWithBudgetWarning(String, Usage)}, stopped for {@code stopReason}. */
	public FakeLlmPort replyWithBudgetWarning(String text, StopReason stopReason, Usage usage) {
		behavior.set(new Behavior(text, stopReason, usage, null, true));
		return this;
	}

	public FakeLlmPort failWith(RuntimeException failure) {
		behavior.set(new Behavior(null, null, null, failure, false));
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
		lastResponse = null;
		requests.clear();
		replyWith("Buyurun, efendim.");
	}

	public LlmRequest lastRequest() {
		return lastRequest;
	}

	/** The last response handed out, so a test can check the assistant turn is replayed as the same object. */
	public LlmResponse lastResponse() {
		return lastResponse;
	}

	/** Every request since construction or {@link #reset()}, in order. */
	public List<LlmRequest> requests() {
		return List.copyOf(requests);
	}

	public static LlmUnavailable unavailable() {
		return new LlmUnavailable("fake: Claude unreachable");
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		lastRequest = request;
		requests.add(request);
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
		LlmResponse response = new LlmResponse(
				new LlmMessage.Assistant(current.reply(), List.of(), new ProviderContent(List.of())),
				current.stopReason(), current.usage(), current.budgetWarning());
		lastResponse = response;
		return response;
	}

}
