package ai.alfred;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import fixture.violation.FixtureApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

class ModularityTests {

	static final ApplicationModules MODULES = ApplicationModules.of(AlfredApplication.class,
			new ImportOption.DoNotIncludeTests());

	/**
	 * The architecture spine's dependency graph. Any edge not listed here is forbidden. The leaf modules (persona, llm,
	 * memory) can't express "no dependencies" through {@code allowedDependencies}, so this table is the guard; nothing
	 * lists audit, so no module may depend on it.
	 */
	static final Map<String, Set<String>> ALLOWED = Map.of(
			"agent", Set.of("trust", "persona", "memory", "llm", "capability"),
			"trust", Set.of("persona"),
			"persona", Set.of(),
			"capability", Set.of("trust"),
			"llm", Set.of(),
			"voice", Set.of("agent", "persona"),
			"memory", Set.of(),
			"audit", Set.of("agent", "trust", "capability", "llm"),
			"cli", Set.of("agent", "voice"));

	@Test
	void verifiesModuleBoundaries() {
		MODULES.verify();
	}

	@Test
	void declaresExactlyTheArchitectureModules() {
		assertThat(MODULES.stream().map(ModularityTests::name)).containsExactlyInAnyOrderElementsOf(ALLOWED.keySet());
	}

	@Test
	void dependsOnlyAlongAllowedEdges() {
		MODULES.forEach(module -> assertThat(dependencies(module))
				.as("dependencies of module '%s'", name(module))
				.isSubsetOf(ALLOWED.get(name(module))));
	}

	@Test
	void keepsModelProviderTypesInsideTheLlmModule() {
		noClasses().that()
			.resideOutsideOfPackage("ai.alfred.llm..")
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage("com.anthropic..", "org.springframework.ai..")
			.because("Anthropic and Spring AI types must not leak past LlmPort (AD-2, AD-11)")
			.check(new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("ai.alfred"));
	}

	@Test
	void verificationFailsWhenAModuleUsesAnotherModulesInternals() {
		assertThatExceptionOfType(Violations.class)
				// The fixture lives in test sources, which Modulith skips by default.
				.isThrownBy(() -> ApplicationModules.of(FixtureApplication.class, location -> true).verify())
				.withMessageContaining("AlphaSecret");
	}

	private static Set<String> dependencies(ApplicationModule module) {
		return module.getDirectDependencies(MODULES).uniqueModules().map(ModularityTests::name)
				.collect(Collectors.toSet());
	}

	private static String name(ApplicationModule module) {
		return module.getIdentifier().toString();
	}

}
