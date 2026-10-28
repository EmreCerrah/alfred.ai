package ai.alfred.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns token counts into USD. The prompt length (input + cache read + cache write) picks the rate card for the whole
 * call; cache reads and writes are priced as multiples of the input rate.
 */
class Pricing {

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	private static final int SCALE = 8;

	private final MeteringProperties.Prices prices;

	Pricing(MeteringProperties.Prices prices) {
		this.prices = prices;
	}

	BigDecimal cost(Usage usage) {
		long prompt = usage.inputTokens() + usage.cacheReadTokens() + usage.cacheWriteTokens();
		boolean longContext = prompt > prices.longContextThreshold();
		BigDecimal input = longContext ? prices.longInputPerMtok() : prices.inputPerMtok();
		BigDecimal output = longContext ? prices.longOutputPerMtok() : prices.outputPerMtok();
		BigDecimal micro = input.multiply(BigDecimal.valueOf(usage.inputTokens()))
			.add(input.multiply(prices.cacheReadMultiplier()).multiply(BigDecimal.valueOf(usage.cacheReadTokens())))
			.add(input.multiply(prices.cacheWriteMultiplier()).multiply(BigDecimal.valueOf(usage.cacheWriteTokens())))
			.add(output.multiply(BigDecimal.valueOf(usage.outputTokens())));
		return micro.divide(MILLION, SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * The most a call can cost: every estimated prompt token written to the cache (the dearest input) plus a full
	 * {@code maxTokens} of output. Reserved before the call so the budget can never be overshot.
	 */
	BigDecimal reservation(long estimatedPromptTokens, long maxTokens) {
		return cost(new Usage(0, maxTokens, 0, estimatedPromptTokens)).max(cost(new Usage(estimatedPromptTokens,
				maxTokens, 0, 0)));
	}

}
