package ai.alfred.llm;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The {@code llm.call_ledger} table. Checking the limits and reserving a call happen in one transaction under one
 * advisory lock, so concurrent Tasks cannot pass the budget together (AD-12). Any database failure before the call is
 * fail-closed: {@link LlmUnavailable}, no call.
 */
class CostLedger {

	private static final Logger log = LoggerFactory.getLogger(CostLedger.class);

	/** Arbitrary, stable key for {@code pg_advisory_xact_lock}: serialises reservations across the daemon. */
	static final long BUDGET_LOCK = 0x616c66726564L;

	/** A reserved call; {@code month} is the budget month it is counted in, even if it settles after midnight. */
	record Reservation(UUID id, String month) {
	}

	private final JdbcClient jdbc;

	private final TransactionTemplate tx;

	private final MeteringProperties properties;

	private final Clock clock;

	CostLedger(JdbcClient jdbc, TransactionTemplate tx, MeteringProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.tx = tx;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @throws LlmTurnLimitReached when the Task has used its Turns
	 * @throws LlmBudgetExhausted when the month's spend plus pending reservations plus {@code estimate} would pass the
	 * budget
	 * @throws LlmUnavailable when the ledger cannot be written
	 */
	Reservation reserve(UUID taskId, String model, BigDecimal estimate) {
		try {
			return tx.execute(status -> {
				jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", BUDGET_LOCK).query().singleValue();
				int turns = jdbc.sql("SELECT count(*) FROM llm.call_ledger WHERE task_id = :task")
					.param("task", taskId)
					.query(Integer.class)
					.single();
				if (turns >= properties.maxTurnsPerTask()) {
					throw new LlmTurnLimitReached(
							"Task %s used its %d Turns".formatted(taskId, properties.maxTurnsPerTask()),
							properties.maxTurnsPerTask());
				}
				String month = currentMonth();
				BigDecimal spent = monthTotal(month);
				if (spent.add(estimate).compareTo(properties.monthlyBudgetUsd()) > 0) {
					throw new LlmBudgetExhausted("Month %s: %s USD spent or reserved, %s more would pass %s USD"
						.formatted(month, spent, estimate, properties.monthlyBudgetUsd()), turns);
				}
				UUID id = UUID.randomUUID();
				jdbc.sql("""
						INSERT INTO llm.call_ledger (id, task_id, model, called_at, budget_month, status, reserved_usd)
						VALUES (:id, :task, :model, :at, :month, 'RESERVED', :reserved)
						""")
					.param("id", id)
					.param("task", taskId)
					.param("model", model)
					.param("at", Timestamp.from(Instant.now(clock)))
					.param("month", month)
					.param("reserved", estimate)
					.update();
				return new Reservation(id, month);
			});
		}
		catch (DataAccessException ex) {
			throw new LlmUnavailable("The cost ledger cannot be written: " + ex.getMostSpecificCause().getMessage(), ex);
		}
		catch (TransactionException ex) {
			// e.g. no connection at all: the database is down, which is the commonest way the ledger is unwritable.
			throw new LlmUnavailable("The cost ledger cannot be written: " + ex.getMostSpecificCause().getMessage(), ex);
		}
	}

	/** Records the real cost. A failure here only logs: the call happened, and its reservation still counts. */
	void settle(UUID id, Usage usage, BigDecimal cost) {
		try {
			jdbc.sql("""
					UPDATE llm.call_ledger SET status = 'SETTLED', cost_usd = :cost, input_tokens = :input,
					       output_tokens = :output, cache_read_tokens = :cacheRead, cache_write_tokens = :cacheWrite
					WHERE id = :id
					""")
				.param("cost", cost)
				.param("input", usage.inputTokens())
				.param("output", usage.outputTokens())
				.param("cacheRead", usage.cacheReadTokens())
				.param("cacheWrite", usage.cacheWriteTokens())
				.param("id", id)
				.update();
		}
		catch (DataAccessException ex) {
			log.error("Could not settle ledger entry {}; its reservation keeps counting", id, ex);
		}
	}

	/** The call failed before Claude billed it: it costs nothing, but it was a Turn. */
	void settleFailed(UUID id) {
		try {
			jdbc.sql("UPDATE llm.call_ledger SET status = 'FAILED', cost_usd = 0 WHERE id = :id").param("id", id).update();
		}
		catch (DataAccessException ex) {
			log.error("Could not mark ledger entry {} as failed; its reservation keeps counting", id, ex);
		}
	}

	/**
	 * True exactly once per month: the first time the month's settled spend reaches the warning ratio of the budget.
	 * Pending reservations of other calls do not count here, so a worst-case estimate cannot use the warning up.
	 */
	boolean claimBudgetWarning(String month) {
		try {
			BigDecimal threshold = properties.monthlyBudgetUsd().multiply(properties.budgetWarningRatio());
			BigDecimal settled = jdbc.sql("SELECT coalesce(sum(cost_usd), 0) FROM llm.call_ledger "
					+ "WHERE budget_month = :month AND status <> 'RESERVED'")
				.param("month", month)
				.query(BigDecimal.class)
				.single();
			if (settled.compareTo(threshold) < 0) {
				return false;
			}
			return jdbc.sql("""
					INSERT INTO llm.budget_warning (budget_month, warned_at) VALUES (:month, :at)
					ON CONFLICT (budget_month) DO NOTHING
					""").param("month", month).param("at", Timestamp.from(Instant.now(clock))).update() == 1;
		}
		catch (DataAccessException | TransactionException ex) {
			log.error("Could not check the budget warning", ex);
			return false;
		}
	}

	/** Settled costs plus pending reservations of the current month, in {@code alfred.llm.budget-zone}. */
	BigDecimal monthToDate() {
		return monthTotal(currentMonth());
	}

	private BigDecimal monthTotal(String month) {
		return jdbc.sql("SELECT coalesce(sum(coalesce(cost_usd, reserved_usd)), 0) FROM llm.call_ledger "
				+ "WHERE budget_month = :month")
			.param("month", month)
			.query(BigDecimal.class)
			.single();
	}

	private String currentMonth() {
		return YearMonth.now(clock.withZone(properties.budgetZone())).toString();
	}

}
