package ai.alfred.llm;

import java.math.BigDecimal;

/**
 * The only {@link LlmPort}: every call is checked against the Turn limit and the monthly budget, reserved in the
 * ledger, made, and settled with its real cost (AD-12). Refusals are typed and fail-closed.
 */
class MeteredLlmPort implements LlmPort {

	private final ModelCall model;

	private final CostLedger ledger;

	private final Pricing pricing;

	MeteredLlmPort(ModelCall model, CostLedger ledger, Pricing pricing) {
		this.model = model;
		this.ledger = ledger;
		this.pricing = pricing;
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		BigDecimal estimate = pricing.reservation(model.estimatePromptTokens(request), model.maxTokens());
		CostLedger.Reservation reservation = ledger.reserve(request.taskId(), model.model(), estimate);
		LlmResponse response;
		try {
			response = model.call(request);
		}
		catch (RuntimeException | Error ex) {
			ledger.settleFailed(reservation.id());
			throw ex;
		}
		ledger.settle(reservation.id(), response.usage(), pricing.cost(response.usage()));
		return ledger.claimBudgetWarning(reservation.month()) ? response.withBudgetWarning() : response;
	}

}
