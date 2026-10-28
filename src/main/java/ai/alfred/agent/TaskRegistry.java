package ai.alfred.agent;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The active Tasks, by {@code taskId}. There is no global "current task": how many may run at once is a policy
 * ({@code alfred.agent.max-active-tasks}), not a structural assumption (AD-9).
 */
class TaskRegistry {

	record Task(UUID taskId, Instant startedAt) {
	}

	private final Map<UUID, Task> active = new ConcurrentHashMap<>();

	private final int maxActiveTasks;

	TaskRegistry(int maxActiveTasks) {
		this.maxActiveTasks = maxActiveTasks;
	}

	/**
	 * Starts a new Task, or returns empty when the limit is reached. Synchronized so two requests can't both take the
	 * last slot.
	 */
	synchronized Optional<Task> tryStart() {
		if (active.size() >= maxActiveTasks) {
			return Optional.empty();
		}
		Task task = new Task(UUID.randomUUID(), Instant.now());
		active.put(task.taskId(), task);
		return Optional.of(task);
	}

	void finish(UUID taskId) {
		active.remove(taskId);
	}

	int activeCount() {
		return active.size();
	}

}
