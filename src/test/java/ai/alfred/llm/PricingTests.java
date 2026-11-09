package ai.alfred.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PricingTests {

	static final MeteringProperties.Prices HAIKU_5_5 = new MeteringProperties.Prices(new BigDecimal("0.10"),
			new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("2.50"), 100_000, new BigDecimal("0.1"),
			new BigDecimal("1.25"));

	final Pricing pricing = new Pricing(HAIKU_5_5);

	@Test
	void pricesAShortPromptOnTheStandardCard() {
		// 10k input * 0.10 + 2k output * 0.50 = 1000 + 1000 micro-dollars
		assertThat(pricing.cost(new Usage(10_000, 2_000, 0, 0))).isEqualByComparingTo("0.002");
	}

	@Test
	void pricesCacheReadsAndWritesAsMultiplesOfTheInputRate() {
		// 40k read * 0.10 * 0.1 = 0.0004; 50k write * 0.10 * 1.25 = 0.00625 (both under the long-context threshold)
		assertThat(pricing.cost(new Usage(0, 0, 40_000, 0))).isEqualByComparingTo("0.0004");
		assertThat(pricing.cost(new Usage(0, 0, 0, 50_000))).isEqualByComparingTo("0.00625");
	}

	@Test
	void switchesTheWholeCallToTheLongCardAboveTheThreshold() {
		// 90k input + 20k cache read = 110k prompt > 100k: input 0.50, read 0.05, output 2.50
		BigDecimal cost = pricing.cost(new Usage(90_000, 1_000, 20_000, 0));

		assertThat(cost).isEqualByComparingTo(new BigDecimal("0.045").add(new BigDecimal("0.001"))
			.add(new BigDecimal("0.0025")));
	}

	@Test
	void reservesTheWorstCaseOfThePromptAndAFullOutput() {
		// 1000 prompt tokens written to cache (0.000125) + 4000 output tokens (0.002)
		assertThat(pricing.reservation(1_000, 4_000)).isEqualByComparingTo("0.002125");
		assertThat(pricing.reservation(1_000, 4_000)).isGreaterThanOrEqualTo(pricing.cost(new Usage(1_000, 4_000, 0, 0)));
	}

	@Test
	void keepsEightDecimals() {
		assertThat(pricing.cost(new Usage(1, 0, 0, 0)).scale()).isEqualTo(8);
		assertThat(pricing.cost(new Usage(1, 0, 0, 0))).isEqualByComparingTo("0.0000001");
	}

}
