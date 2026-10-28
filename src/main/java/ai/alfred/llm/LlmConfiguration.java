package ai.alfred.llm;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ LlmProperties.class, MeteringProperties.class })
class LlmConfiguration {

	@Bean
	LlmPort llmPort(ModelCall model, JdbcClient jdbc, TransactionTemplate tx, MeteringProperties metering) {
		return new MeteredLlmPort(model, new CostLedger(jdbc, tx, metering, Clock.systemUTC()),
				new Pricing(metering.pricing()));
	}

}
