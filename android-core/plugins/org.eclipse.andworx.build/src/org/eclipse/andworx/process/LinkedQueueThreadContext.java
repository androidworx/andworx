/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.process;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.android.annotations.NonNull;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.QueueThreadContext;

/** 
 * Interface for notification of queue events, creation, task running and destruction. 
 * Implementation of 2 linked job queues to support task running.
 * @param <T> Process for job to run
 */
public class LinkedQueueThreadContext<T> implements QueueThreadContext<T> {
    // List of outstanding jobs.
    @NonNull
    protected final Map<Integer, ConcurrentLinkedQueue<QueuedJob<T>>> outstandingJobs;
    // Llist of finished jobs.
    @NonNull
    protected final Map<Integer, ConcurrentLinkedQueue<QueuedJob<T>>> doneJobs;

    /**
     * Construct a LinkedQueueThreadContext object
     */
    public LinkedQueueThreadContext() {
    	outstandingJobs = new ConcurrentHashMap<>();
    	doneJobs = new ConcurrentHashMap<>();
    }

    /**
     * Start session
     * @param key Session id
     */
    public synchronized void start(int key) {
        outstandingJobs.put(key, new ConcurrentLinkedQueue<>());
        doneJobs.put(key, new ConcurrentLinkedQueue<>());
    }

    /**
     * Poll outstanding jobs for next job to run
     * @param key
     * @return
     */
    public Job<T> pollOutstanding(int key) {
    	return outstandingJobs.get(key).poll();
    }
 
    /**
     * Poll finished jobs for next completed job.
     * This is used to test for jobs which have thrown exceptions.
     * @param key Session id
     * @return Job object
     */
    public Job<T> pollDone(int key) {
    	return doneJobs.get(key).poll();
    }
 
    /**
     * Clear queues
     * @param key Session id
     */
    public void clear(int key) {
    	outstandingJobs.get(key).clear();
    	doneJobs.clear();
    }

    /**
     * Asssure outstanding jobs list exists for current session
     * @param key Session id
     */
    public void assureOutstandingJobsList(int key) {
        synchronized (outstandingJobs) {
            outstandingJobs.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        }
    }

    /**
     * Notification of a scheduled task execution.
     * @param job Job that should be executed on the current thread.
     */
	@Override
	public void runTask(Job<T> job) throws Exception {
        job.runTask(null);
        QueuedJob<T> queuedJob = (QueuedJob<T>)job;
        int key = queuedJob.key;
        outstandingJobs.get(key).remove(job);

        synchronized (doneJobs) {
            ConcurrentLinkedQueue<QueuedJob<T>> jobs =
                    doneJobs.computeIfAbsent(
                            key, k -> new ConcurrentLinkedQueue<>());

            jobs.add(queuedJob);
        }
	}
	
    /**
     * Notification of a new worker thread association with the queue
     *
     * @param thread Thread being associated.
     * @return true if creation was successful.
     */
    @Override
    public boolean creation(@NonNull Thread thread) throws IOException, InterruptedException {
    	// Default to do nothing (Original AAPT design had establish a command shell)
    	return true;
    }

    /**
     * Notification of the removal of the passed thread as a queue worker thread.
     * @param thread Removed thread.
     */
   @Override
    public void destruction(@NonNull Thread thread) throws IOException, InterruptedException {
    }

   /**
    * Notification of the queue temporary shutdown. All native resources must be released.
    * Once shutdown is called, at least one {@link #creation} must be made before any call to
    * {@link #runTask}.
    */
    @Override
    public void shutdown() {
    }
}
