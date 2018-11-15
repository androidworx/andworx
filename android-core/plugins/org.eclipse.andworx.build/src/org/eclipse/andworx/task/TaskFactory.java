/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.task;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.andworx.log.SdkLogger;

/**
 * Creates build tasks and places them on a queue so they can be executed sequentially.
 * Call factory start() method to commence building activity.
 * Wait on this object to block the caller thread until the queue is cleared. 
 * Call factory stop() method to clean up resources and it therefore should be called from a finally clause.
 */
public class TaskFactory {
    public static int MAX_QUEUE_LENGTH = 16;
    
    private static SdkLogger logger = SdkLogger.getLogger(TaskFactory.class.getName());
	
    private BlockingQueue<AndroidBuildJob> taskQueue;
    private Thread consumeThread;
    private AtomicInteger sessionCount;
    private boolean hasSession;

    /**
     * Construct TaskFactory object
     */
    public TaskFactory() {
    	// Queue capacity allows for a generous maximum number of tasks to be queued
    	// Fair access for waiting threads is turned on but should not be needed
    	taskQueue = new ArrayBlockingQueue<AndroidBuildJob>(MAX_QUEUE_LENGTH, true);
    	sessionCount = new AtomicInteger();
    }

	/** 
	 * Creates a standard build task 
	 */
	public AndroidBuildJob create(BuildTask buildTask) {
		AndroidBuildJob task = new AndroidBuildJob(buildTask);
		taskQueue.offer(task);
		return task;
	}

	/**
	 * Start service
	 */
	public synchronized void start() {
		hasSession = true;
        sessionCount.incrementAndGet();
		// Only action once
		if ((consumeThread != null) && consumeThread.isAlive())
			return;
		final TaskFactory self = this;
		// Create consumer thread
        Runnable comsumeTask = new Runnable()
        {
            @Override
            public void run() 
            {
                while (true)
                {
                    try 
                    {
                    	AndroidBuildJob task = taskQueue.take();
                   	    if (task != null) { // Paranoid null check
                            task.schedule();
                            synchronized(task) {
                            	logger.verbose("Waiting for %s", task.getName());
                        	    task.wait();
                        	    logger.verbose("%s ok = %s", task.getName(), task.getStatus().isOK());
                            }
                    	} else break; // This is not expected to happen
                    	if (taskQueue.isEmpty())
                    		// Notify when queue becomes empty
	                        synchronized(self) {
	                        	self.notifyAll();
	                        }
                    } 
                    catch (InterruptedException e) 
                    {
                		// Notify when interrupt stops service
                        synchronized(self) {
                        	self.notifyAll();
                        }
                        break;
                    }
                }
                while (!taskQueue.isEmpty()) {
                	// Attempt to cancel outstanding jobs
        			taskQueue.remove().cancel();
        		}
           }
        };
        consumeThread = new Thread(comsumeTask, "Task Service");
        consumeThread.start();
	}

	/*
	 * Stop service
	 */
	public synchronized void stop() {

		if (hasSession) {
			hasSession = (sessionCount.decrementAndGet() > 0);
			// Only continue when all sessions have been stopped
			if (hasSession)
				return;
		}
		if (consumeThread != null) {
			consumeThread.interrupt();
		    consumeThread = null;
		}
	}
}
