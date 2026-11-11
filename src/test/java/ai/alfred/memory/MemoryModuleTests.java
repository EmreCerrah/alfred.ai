package ai.alfred.memory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The Conversation lives in memory only (AD-16): nothing in the module can reach a database, a file or a clock.
 */
class MemoryModuleTests {

	static final JavaClasses MEMORY = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
		.importPackages("ai.alfred.memory");

	@Test
	void importsTheModule() {
		assertThat(MEMORY.contain(Conversation.class)).isTrue();
	}

	@Test
	void neverTouchesADatabase() {
		noClasses().should()
			.dependOnClassesThat()
			.resideInAnyPackage("java.sql..", "javax.sql..", "org.springframework.jdbc..", "org.flywaydb..",
					"org.postgresql..")
			.because("the Conversation is never written to Postgres (AD-16)")
			.check(MEMORY);
	}

	@Test
	void neverWritesToDiskOrReadsAClock() {
		noClasses().should()
			.dependOnClassesThat()
			.resideInAnyPackage("java.io..", "java.nio..", "java.time..", "java.util.concurrent..")
			.because("the Conversation is not persisted and never expires (FR-5)")
			.check(MEMORY);
	}

	@Test
	void hasNoMigrations() throws Exception {
		assertThat(new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/memory/**"))
			.isEmpty();
	}

}
