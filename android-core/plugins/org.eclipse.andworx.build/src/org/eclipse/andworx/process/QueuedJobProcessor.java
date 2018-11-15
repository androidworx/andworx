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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.android.annotations.NonNull;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.WorkQueue;
import com.android.utils.ILogger;

/**
 * Implementation of {@link JobProcessor} that queues jobs and uses a pool of server
 * processes to serve those.
 *
 * @param <T> Process for job to run
 * @param <R> Process return type
 */
public abstract class QueuedJobProcessor <T,R>implements JobProcessor {
    /** Maximum number of concurrent processes to launch. */
    private static final int MAX_DEFAULT_NUMBER_PROCESSES = 8;
    /** Number of concurrent processes to launch. */
    protected static final int DEFAULT_NUMBER_PROCESSES =
            Integer.min(
                    MAX_DEFAULT_NUMBER_PROCESSES,
                    Runtime.getRuntime().availableProcessors());

    @NonNull 
    protected final ILogger logger;
    /** Queue responsible for handling all passed jobs with a pool of worker threads. */
    @NonNull
    /** Thread context with linked job queues */
    protected final LinkedQueueThreadContext<T> threadContext;
    
    @NonNull 
    protected final WorkQueue<T> processingRequests;
    /** ref count of active users, if it drops to zero, that means there are no more active users
        and the queue should be shutdown. */
    @NonNull 
    protected final AtomicInteger refCount;
    /** Maximum number of processes */
    private int processToUse;

    /** Per process unique key provider to remember which users enlisted which requests. */
    @NonNull protected final AtomicInteger keyProvider;

    /**
     * Construct QueuedJobProcessor object
     * @param logger Logger
     * @param processesNumber Maximum number of processes to run concurrently
     */
    public QueuedJobProcessor(ILogger logger, int processesNumber) {
    	this.threadContext =  new LinkedQueueThreadContext<>();
    	this.logger = logger;
    	refCount = new AtomicInteger(0);
    	keyProvider = new AtomicInteger(0); //QueueThreadContext
        if (processesNumber > 0) {
            processToUse = processesNumber;
        } else {
            processToUse = DEFAULT_NUMBER_PROCESSES;
        }
        processingRequests =
                new WorkQueue<>(
                        logger, threadContext, "queued-resource-processor", processToUse, 0);
	}

	/**
	 * Returns maximum number of processes
	 * @return int
	 */
	@Override
    public int getParallelism() {
    	return processToUse;
    }
    
	/**
	 * Start new session
	 * @return session id key
	 */
    @Override
    public synchronized int start() {
        // increment our reference count.
        refCount.incrementAndGet();

        // get a unique key for the lifetime of this process.
        int key = keyProvider.incrementAndGet();
        threadContext.start(key);
        return key;
    }

	/**
	 * End session
	 * @param key Session id
	 * @throws InterruptedException
	 */
    @Override
    public synchronized void end(int key) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            waitForAll(key);
            threadContext.clear(key);
        } finally {
            // even if we have failures, we need to shutdown property the sub processes.
            if (refCount.decrementAndGet() == 0) {
                try {
                    processingRequests.shutdown();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    logger.warning(
                            "Error while shutting down queued resource proecssor queue : %s",
                            e.getMessage());
                }
                logger.verbose(
                        "Shutdown finished in %1$dms", System.currentTimeMillis() - startTime);
            }
        }
    }

    /**
     * Wait for all jobs to end
     * @param key Session key
     * @throws InterruptedException
     */
    protected void waitForAll(int key) throws InterruptedException {
        Job<T> processJob;
        boolean hasExceptions = false;

        // Some jobs could be still waiting to start. Wait for them to finish and check for any
        // issues.
        while ((processJob = threadContext.pollOutstanding(key)) != null) {
            logger.verbose(
                    "Thread(%1$s) : wait for {%2$s)",
                    Thread.currentThread().getName(), processJob.toString());
            try {
                processJob.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                logger.verbose(
                        "Exception while processing job : "
                                + processJob.toString()
                                + " : "
                                + e.getCause());
                hasExceptions = true;
            }
        }

        // Some jobs could have started before this method was called, so wait for them to finish
        // (some are probably already done) and check for any issues.
        while ((processJob = threadContext.pollDone(key)) != null) {
            try {
                processJob.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                logger.verbose(
                        "Exception while processing job : "
                                + processJob.toString()
                                + " : "
                                + e.getCause());
                hasExceptions = true;
            }
        }
        if (hasExceptions) {
            throw new RuntimeException("Some file processing failed, see logs for details");
        }
    }

}
