package ai.alfred;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PostgresRequiredFailureAnalyzerTests {

	final PostgresRequiredFailureAnalyzer analyzer = new PostgresRequiredFailureAnalyzer();

	@ParameterizedTest
	@ValueSource(strings = { "08001", "08006", "28P01", "28000", "3D000" })
	void explainsUnreachableOrMisconfiguredDatabase(String sqlState) {
		SQLException failure = new SQLException("boom", sqlState);

		assertThat(analyzer.analyze(failure).getDescription()).startsWith("alfred requires PostgreSQL");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "42P01", "23505" })
	void leavesOtherSqlFailuresAlone(String sqlState) {
		assertThat(analyzer.analyze(new SQLException("boom", sqlState))).isNull();
	}

}
