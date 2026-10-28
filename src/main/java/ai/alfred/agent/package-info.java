/**
 * Decision loop: Task lifecycle, verification, cancellation, Tier 1 warning state, input routing. Core domain.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "trust", "persona", "memory", "llm", "capability" })
package ai.alfred.agent;
