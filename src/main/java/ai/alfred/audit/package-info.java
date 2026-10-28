/**
 * Append-only AuditLog fed by events; day summary Tool. No module may depend on audit.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "agent", "trust", "capability", "llm" })
package ai.alfred.audit;
