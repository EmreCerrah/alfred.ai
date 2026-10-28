package ai.alfred;

import java.sql.SQLException;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a startup failure caused by an unreachable or misconfigured database into an explicit message: alfred does not
 * start without PostgreSQL, because no Action may run without a write-ahead audit record (AD-7).
 */
class PostgresRequiredFailureAnalyzer extends AbstractFailureAnalyzer<SQLException> {

	// SQLSTATE: class 08 = connection exception, class 28 = invalid authorization, 3D000 = database does not exist
	private static final String CONNECTION_EXCEPTION_CLASS = "08";

	private static final String INVALID_AUTHORIZATION_CLASS = "28";

	private static final String INVALID_CATALOG_NAME = "3D000";

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, SQLException cause) {
		String sqlState = cause.getSQLState();
		if (sqlState == null || !(sqlState.startsWith(CONNECTION_EXCEPTION_CLASS)
				|| sqlState.startsWith(INVALID_AUTHORIZATION_CLASS) || sqlState.equals(INVALID_CATALOG_NAME))) {
			return null;
		}
		return new FailureAnalysis(
				"alfred requires PostgreSQL and will not start without it. Connecting to the database failed: "
						+ cause.getMessage(),
				"Start the database with 'docker compose up -d' and check ALFRED_DB_NAME, ALFRED_DB_USER and ALFRED_DB_PASSWORD (or ALFRED_DB_URL).",
				cause);
	}

}
