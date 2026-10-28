package ai.alfred.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import ai.alfred.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The ledger and its limits against a real Postgres. Each successful scripted call costs exactly 0.002 USD (4000
 * output tokens), and so does its reservation, which keeps the arithmetic exact.
 */
@SpringBootTest(properties = "ALFRED_DB_PASSWORD=unused-by-service-connection")
@Import(TestcontainersConfiguration.class)
class MeteredLlmPortTests {

	static final Usage CALL = new Usage(0, 4_000, 0, 0);

	/** 2026-10-31 23:30 in Istanbul (UTC+3): still October there. */
	static final Instant LAST_EVENING_OF_OCTOBER = Instant.parse("2026-10-31T20:30:00Z");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	DataSource dataSource;

	final MutableClock clock = new MutableClock(LAST_EVENING_OF_OCTOBER);

	final ScriptedModel model = new ScriptedModel();

	@BeforeEach
	void emptyLedger() {
		jdbc.sql("DELETE FROM llm.call_ledger").update();
		jdbc.sql("DELETE FROM llm.budget_warning").update();
	}

	@Test
	void recordsEveryCallWithItsTaskTokensAndCost() {
		UUID taskId = UUID.randomUUID();

		port("1", 15).complete(request(taskId));

		var row = jdbc.sql("SELECT task_id, model, status, cost_usd, output_tokens, budget_month FROM llm.call_ledger")
			.query((rs, n) -> List.of(rs.getObject("task_id"), rs.getString("model"), rs.getString("status"),
					rs.getBigDecimal("cost_usd"), rs.getLong("output_tokens"), rs.getString("budget_month")))
			.single();
		assertThat(row.get(0)).isEqualTo(taskId);
		assertThat(row.get(1)).isEqualTo("claude-haiku-5-5");
		assertThat(row.get(2)).isEqualTo("SETTLED");
		assertThat((BigDecimal) row.get(3)).isEqualByComparingTo("0.002");
		assertThat(row.get(4)).isEqualTo(4000L);
		assertThat(row.get(5)).isEqualTo("2026-10");
	}

	@Test
	void warnsOnceWhenTheMonthPassesEightyPercentAndRefusesTheCallThatWouldPassTheBudget() {
		MeteredLlmPort port = port("0.01", 15);
		List<Boolean> warnings = new ArrayList<>();

		for (int i = 0; i < 5; i++) {
			warnings.add(port.complete(request(UUID.randomUUID())).budgetWarning());
		}

		// 0.008 after the fourth call is 80%; the fifth brings it to exactly 0.010.
		assertThat(warnings).containsExactly(false, false, false, true, false);
		int callsSoFar = model.calls.get();
		int rowsSoFar = rows();
		LlmBudgetExhausted refused = catchThrowableOfType(LlmBudgetExhausted.class,
				() -> port.complete(request(UUID.randomUUID())));
		assertThat(refused).isNotNull();
		assertThat(refused.turnsUsed()).as("a fresh Task: no Turn yet").isZero();
		assertThat(model.calls.get()).as("the model is never called").isEqualTo(callsSoFar);
		assertThat(rows()).as("a refused call reserves nothing").isEqualTo(rowsSoFar);
	}

	@Test
	void stopsATaskWhoseNextCallWouldPassTheBudget() {
		MeteredLlmPort port = port("0.005", 15);
		UUID taskId = UUID.randomUUID();
		port.complete(request(taskId));
		port.complete(request(taskId));

		assertThatExceptionOfType(LlmBudgetExhausted.class).isThrownBy(() -> port.complete(request(taskId)))
			.satisfies(ex -> assertThat(ex.turnsUsed()).isEqualTo(2));
	}

	@Test
	void refusesTheTurnAfterTheLimitWithoutCallingTheModel() {
		MeteredLlmPort port = port("5", 3);
		UUID taskId = UUID.randomUUID();
		for (int i = 0; i < 3; i++) {
			port.complete(request(taskId));
		}

		assertThatExceptionOfType(LlmTurnLimitReached.class).isThrownBy(() -> port.complete(request(taskId)))
			.satisfies(ex -> assertThat(ex.limit()).isEqualTo(3));
		assertThat(model.calls.get()).isEqualTo(3);
		assertThat(rows()).as("the refused Turn is not written").isEqualTo(3);
		// Another Task has its own Turns.
		port.complete(request(UUID.randomUUID()));
	}

	@Test
	void countsAFailedCallAsATurnThatCostsNothing() {
		MeteredLlmPort port = port("5", 2);
		UUID taskId = UUID.randomUUID();
		model.failNext = new LlmUnavailable("down");

		assertThatExceptionOfType(LlmUnavailable.class).isThrownBy(() -> port.complete(request(taskId)));

		assertThat(jdbc.sql("SELECT status FROM llm.call_ledger").query(String.class).single()).isEqualTo("FAILED");
		assertThat(ledger("5", 2).monthToDate()).isEqualByComparingTo("0");
		port.complete(request(taskId));
		assertThatExceptionOfType(LlmTurnLimitReached.class).isThrownBy(() -> port.complete(request(taskId)));
	}

	@Test
	void makesNoCallWhenTheLedgerCannotBeWritten() {
		MeteredLlmPort port = port("5", 15);
		jdbc.sql("ALTER TABLE llm.call_ledger RENAME TO call_ledger_offline").update();
		try {
			assertThatExceptionOfType(LlmUnavailable.class).isThrownBy(() -> port.complete(request(UUID.randomUUID())))
				.withMessageContaining("cost ledger");
			assertThat(model.calls.get()).isZero();
		}
		finally {
			jdbc.sql("ALTER TABLE llm.call_ledger_offline RENAME TO call_ledger").update();
		}
	}

	@Test
	void reportsAnUnreachableDatabaseAsLlmUnavailableWithoutCallingTheModel() {
		DriverManagerDataSource dead = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/alfred", "alfred", "x");
		CostLedger ledger = new CostLedger(JdbcClient.create(dead),
				new TransactionTemplate(new DataSourceTransactionManager(dead)), properties("5", 15), clock);
		MeteredLlmPort port = new MeteredLlmPort(model, ledger, new Pricing(PricingTests.HAIKU_5_5));

		assertThatExceptionOfType(LlmUnavailable.class).isThrownBy(() -> port.complete(request(UUID.randomUUID())))
			.withMessageContaining("cost ledger");
		assertThat(model.calls.get()).isZero();
	}

	@Test
	void keepsTheAnswerWhenTheLedgerFailsAfterThePaidCall() {
		MeteredLlmPort port = port("5", 15);
		model.beforeReturn = () -> jdbc.sql("ALTER TABLE llm.call_ledger RENAME TO call_ledger_offline").update();
		try {
			LlmResponse response = port.complete(request(UUID.randomUUID()));

			assertThat(response.message().text()).isEqualTo("Buyurun, efendim.");
			assertThat(response.budgetWarning()).isFalse();
		}
		finally {
			jdbc.sql("ALTER TABLE llm.call_ledger_offline RENAME TO call_ledger").update();
		}
		assertThat(jdbc.sql("SELECT status FROM llm.call_ledger").query(String.class).single())
			.as("the reservation keeps counting").isEqualTo("RESERVED");
	}

	@Test
	void keepsTheModelsOwnErrorWhenTheLedgerAlsoFails() {
		MeteredLlmPort port = port("5", 15);
		model.beforeReturn = () -> jdbc.sql("ALTER TABLE llm.call_ledger RENAME TO call_ledger_offline").update();
		model.failNext = new LlmUnavailable("model down");
		try {
			assertThatExceptionOfType(LlmUnavailable.class).isThrownBy(() -> port.complete(request(UUID.randomUUID())))
				.withMessage("model down");
		}
		finally {
			jdbc.sql("ALTER TABLE llm.call_ledger_offline RENAME TO call_ledger").update();
		}
	}

	@Test
	void settlesACallThatEndedInAnErrorAsFailed() {
		MeteredLlmPort port = port("5", 15);
		model.failNext = new AssertionError("boom");

		assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> port.complete(request(UUID.randomUUID())));

		assertThat(jdbc.sql("SELECT status FROM llm.call_ledger").query(String.class).single()).isEqualTo("FAILED");
	}

	@Test
	void reservesOnlyUnderTheBudgetLock() throws Exception {
		MeteredLlmPort port = port("5", 15);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		// Hold the budget lock on a connection of our own; a reservation must wait for it.
		try (Connection holder = dataSource.getConnection()) {
			try (PreparedStatement lock = holder.prepareStatement("SELECT pg_advisory_lock(?)")) {
				lock.setLong(1, CostLedger.BUDGET_LOCK);
				lock.execute();
			}
			Future<LlmResponse> call = pool.submit(() -> port.complete(request(UUID.randomUUID())));

			Thread.sleep(300);
			assertThat(call).as("blocked behind the budget lock").isNotDone();
			assertThat(model.calls.get()).isZero();

			try (PreparedStatement unlock = holder.prepareStatement("SELECT pg_advisory_unlock(?)")) {
				unlock.setLong(1, CostLedger.BUDGET_LOCK);
				unlock.execute();
			}
			assertThat(call.get(10, TimeUnit.SECONDS).message().text()).isEqualTo("Buyurun, efendim.");
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void letsOnlyOneOfTwoConcurrentCallsTakeTheLastBudget() throws Exception {
		MeteredLlmPort port = port("0.003", 15);
		model.delay = Duration.ofMillis(300);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						port.complete(request(UUID.randomUUID()));
						return "called";
					}
					catch (LlmBudgetExhausted ex) {
						return "refused";
					}
				}));
			}
			start.countDown();

			assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder("called",
					"refused");
			assertThat(ledger("0.003", 15).monthToDate()).isLessThanOrEqualTo(new BigDecimal("0.003"));
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void startsCountingAgainInANewMonthOfTheBudgetZone() {
		MeteredLlmPort port = port("0.004", 15);
		port.complete(request(UUID.randomUUID()));
		assertThat(port.complete(request(UUID.randomUUID())).budgetWarning()).isTrue();
		assertThatExceptionOfType(LlmBudgetExhausted.class).isThrownBy(() -> port.complete(request(UUID.randomUUID())));

		// 2026-10-31 22:30 UTC is already 1 November 01:30 in Istanbul.
		clock.set(Instant.parse("2026-10-31T22:30:00Z"));

		port.complete(request(UUID.randomUUID()));
		assertThat(port.complete(request(UUID.randomUUID())).budgetWarning()).as("warned again in the new month")
			.isTrue();
		assertThat(ledger("0.004", 15).monthToDate()).isEqualByComparingTo("0.004");
	}

	private MeteredLlmPort port(String budget, int maxTurns) {
		return new MeteredLlmPort(model, ledger(budget, maxTurns), new Pricing(PricingTests.HAIKU_5_5));
	}

	private CostLedger ledger(String budget, int maxTurns) {
		return new CostLedger(jdbc, tx, properties(budget, maxTurns), clock);
	}

	private static MeteringProperties properties(String budget, int maxTurns) {
		return new MeteringProperties(new BigDecimal(budget), maxTurns, new BigDecimal("0.8"),
				ZoneId.of("Europe/Istanbul"), PricingTests.HAIKU_5_5);
	}

	private int rows() {
		return jdbc.sql("SELECT count(*) FROM llm.call_ledger").query(Integer.class).single();
	}

	private static LlmRequest request(UUID taskId) {
		return new LlmRequest(taskId, "You are Alfred.", List.of(), List.of(new LlmMessage.User("Merhaba")));
	}

	/** Answers every call with {@link #CALL} usage; can fail once or take its time. */
	static final class ScriptedModel implements ModelCall {

		final AtomicInteger calls = new AtomicInteger();

		volatile Throwable failNext;

		volatile Duration delay = Duration.ZERO;

		/** Runs inside the call, after the reservation: lets a test break the ledger mid-call. */
		volatile Runnable beforeReturn = () -> {
		};

		@Override
		public LlmResponse call(LlmRequest request) {
			calls.incrementAndGet();
			beforeReturn.run();
			Throwable failure = failNext;
			if (failure != null) {
				failNext = null;
				if (failure instanceof Error error) {
					throw error;
				}
				throw (RuntimeException) failure;
			}
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return new LlmResponse(new LlmMessage.Assistant("Buyurun, efendim.", List.of(),
					new ProviderContent(List.of())), StopReason.END_TURN, CALL);
		}

		@Override
		public long estimatePromptTokens(LlmRequest request) {
			return 0;
		}

		@Override
		public String model() {
			return "claude-haiku-5-5";
		}

		@Override
		public long maxTokens() {
			return 4_000;
		}

	}

	static final class MutableClock extends Clock {

		private volatile Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant now) {
			this.now = now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			MutableClock owner = this;
			return new Clock() {

				@Override
				public ZoneId getZone() {
					return zone;
				}

				@Override
				public Clock withZone(ZoneId other) {
					return owner.withZone(other);
				}

				@Override
				public Instant instant() {
					return owner.instant();
				}

			};
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
