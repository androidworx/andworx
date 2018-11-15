/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.eclipse.andworx.aapt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;

import com.android.ide.common.res2.MergedResourceWriter;
import com.android.ide.common.res2.MergedResourceWriter.FileGenerationParameters;
import com.android.ide.common.res2.MergedResourceWriter.FileGenerationWorkAction;
import com.android.ide.common.workers.WorkerExecutorFacade;

/**
 * Creates merged resources content using a processor which submits work items for processing.
 * The processor is modeled on Gradle's WorkerExecutor. Each task is executed in a Job.
 *
 * @param <T> T is the parameter type that will be passed to the {@link Runnable} implementing the
 *     work item. There can be only one parameter that should encapsulate all data necessary to run
 *     the work action.
 */
public class MergedResourceProcessor implements WorkerExecutorFacade<MergedResourceWriter.FileGenerationParameters> {
    public static int MAX_QUEUE_LENGTH = 16;

    /** Listener to handle job completion */
    class JobChangeListener extends JobChangeAdapter {
    	
 		@Override
		public void done(IJobChangeEvent event) {
			jobStatus = event.getResult();
			boolean doTerminate = false;
			synchronized(count) {
				doTerminate = count.decrementAndGet() == 0; 
				if (!doTerminate && (jobStatus == Status.CANCEL_STATUS)) {
					doTerminate = true;
					count.lazySet(0);
				}
			}
			if (doTerminate) {
				stop();
				MergedResourceProcessor mergedResourceProcessor = MergedResourceProcessor.this;
				synchronized(mergedResourceProcessor) {
					mergedResourceProcessor.notifyAll();
				}
			}
		}
    }
    
    /** Status of last job to complete or null if no job has completed */
    private volatile IStatus jobStatus;
    /** Job queue which blocks after a maximum queue length is attained */
    private BlockingQueue<Job> jobQueue;
    /** Service thread */
    private Thread consumeThread;
    /** Job count */
    private AtomicInteger count;
    /** Number of seconds to wait before timing out */
    private long timeoutSeconds;

    /**
     * Construct a MergedResourceProcessor object
     */
    public MergedResourceProcessor() {
    	count = new AtomicInteger();
        jobQueue = new LinkedBlockingQueue<Job>(MAX_QUEUE_LENGTH);
    }
 
    public void setTimeoutSeconds(long timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	/**
     * Returns status of last job to complete
     * @return IStatus object or null if no job has completed
     */
	public IStatus getJobStatus() {
		return jobStatus == null ? Status.CANCEL_STATUS : jobStatus;
	}

    /**
     * Submit a new work action to be performed.
     * @param parameter the parameter instance to pass to the action.
     */
	@Override
	public void submit(final FileGenerationParameters parameter) {
		int jobNo;
		synchronized(count) {
			jobNo = count.getAndIncrement();
			if (jobNo == 0) {
				jobStatus = null;
				if ((consumeThread == null) || !consumeThread.isAlive())
					startCousumeThread();
			}
		}
		Job job = new Job("Run resource processor") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                new FileGenerationWorkAction(parameter).run();
            	return Status.OK_STATUS;
            }
        };
		job.addJobChangeListener(new JobChangeListener() );
        try {
			jobQueue.put(job);
		} catch (InterruptedException e) {
			throw new AndworxException("Resource proecessor interrupted");
		}
	}

    /** 
     * Wait for all submitted work actions to run to completion. 
     */
	@Override
	public void await() {
		synchronized(this) {
			try { 
				if (timeoutSeconds == 0)
					wait();
				else
					wait(timeoutSeconds * 1000);
			} 
			catch (InterruptedException ignore) {
			}
		}
	}

	private synchronized void startCousumeThread() {
        Runnable comsumeTask = new Runnable()
        {
            @Override
            public void run() 
            {
                while (true)
                {
                    try 
                    {
                        jobQueue.take().schedule();
                    } 
                    catch (InterruptedException e) 
                    {
                        break;
                    }
                }
    			while (!jobQueue.isEmpty()) {
    				Job job = jobQueue.remove();
    				job.cancel();
    				
    			}
            }
        };
        consumeThread = new Thread(comsumeTask, "Resource Processor Service");
        consumeThread.start();
	}

	private synchronized void stop() {
		if (consumeThread != null)
		{
			consumeThread.interrupt();
	        consumeThread = null;
		}
	}
	

}
