/**
 * First-party Tools (shell, file, git, docker, browser); executes only ApprovedAction.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "trust" })
package ai.alfred.capability;
