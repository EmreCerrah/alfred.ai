package ai.alfred.llm;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Budget, Turn limit and prices: {@code alfred.llm.monthly-budget-usd}, {@code alfred.llm.max-turns-per-task},
 * {@code alfred.llm.budget-warning-ratio}, {@code alfred.llm.budget-zone} and {@code alfred.llm.pricing.*}. Shares the
 * {@code alfred.llm} prefix with {@link LlmProperties}; invalid values fail at startup.
 */
@ConfigurationProperties("alfred.llm")
record MeteringProperties(BigDecimal monthlyBudgetUsd, int maxTurnsPerTask, BigDecimal budgetWarningRatio,
		ZoneId budgetZone, Prices pricing) {

	/** USD per million tokens; prompts above {@code longContextThreshold} tokens use the long rates throughout. */
	record Prices(BigDecimal inputPerMtok, BigDecimal outputPerMtok, BigDecimal longInputPerMtok,
			BigDecimal longOutputPerMtok, long longContextThreshold, BigDecimal cacheReadMultiplier,
			BigDecimal cacheWriteMultiplier) {

		Prices {
			for (BigDecimal price : new BigDecimal[] { inputPerMtok, outputPerMtok, longInputPerMtok, longOutputPerMtok,
					cacheReadMultiplier, cacheWriteMultiplier }) {
				if (price == null || price.signum() < 0) {
					throw new IllegalArgumentException("alfred.llm.pricing values must be set and not negative");
				}
			}
			if (longContextThreshold <= 0) {
				throw new IllegalArgumentException("alfred.llm.pricing.long-context-threshold must be positive");
			}
		}

	}

	MeteringProperties {
		if (monthlyBudgetUsd == null || monthlyBudgetUsd.signum() <= 0) {
			throw new IllegalArgumentException("alfred.llm.monthly-budget-usd must be positive");
		}
		if (maxTurnsPerTask < 1) {
			throw new IllegalArgumentException("alfred.llm.max-turns-per-task must be at least 1");
		}
		if (budgetWarningRatio == null || budgetWarningRatio.signum() <= 0
				|| budgetWarningRatio.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("alfred.llm.budget-warning-ratio must be in (0, 1]");
		}
		Objects.requireNonNull(budgetZone, "alfred.llm.budget-zone");
		Objects.requireNonNull(pricing, "alfred.llm.pricing");
	}

}
