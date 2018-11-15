package org.eclipse.andworx.process;

import java.util.concurrent.Future;

import com.android.builder.tasks.Job;
import com.android.builder.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Queude Job with session id.
 * A job has a title, a task to execute, a latch to signal its
 * completion and a boolean result for success or failure
 * @param <T> Process for job to run
 */
public class QueuedJob <T> extends Job<T> {

	/** Session ID key */
    protected final int key;

    /**
     * Construct QueuedJob object
     * @param key Session ID key
     * @param jobTitle Job name
     * @param task Task that can be created asynchronously
     * @param resultFuture A {@link Future} that accepts completion listeners
     */
	public QueuedJob(
        int key,
        String jobTitle,
        Task<T> task,
        ListenableFuture<?> resultFuture) {
		super(jobTitle, task, resultFuture);
		this.key = key;
	}
}
