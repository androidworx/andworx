/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.eclipse.andworx.process.java;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.andworx.task.java.AndworxJavaProcessExecutor;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;

import com.android.annotations.NonNull;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.SettableFuture;

/**
 * An executor for external Java-based processes.
 */
@NotThreadSafe
public class JavaProcess{
	/** Jobs are units of runnable work that can be scheduled to be run with the job* manager **/
	private Job job;
	/** Process executor that uses Eclipse JDT to execute external java processes */
	private AndworxJavaProcessExecutor javaExecutor;
    /** Object to return process exit code or rethrow exeception which caused process to fail */
	private ProcessResult processResult;

	/**
	 * Construct JavaProcess object
	 * @param jvmParameters Launch input parameters, including LaunchConfiguration object
	 * @param processOutputHandler Handler for grabbing process output
	 * @param jobResult Future to signal job completion to work queue 
	 * @param actualResult Future to return result to caller
	 */
	public JavaProcess(
     		@NonNull JvmParameters jvmParameters, 
     		@NonNull ProcessOutputHandler processOutputHandler,
     		@NonNull SettableFuture<ProcessResult> jobResult,
     		@NonNull SettableFuture<ProcessResult> actualResult) {
        javaExecutor = new AndworxJavaProcessExecutor();
        String name = jvmParameters.getConfiguration().getName();
		job = new Job(name) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				jvmParameters.setMonitor(monitor);
		        try { 
		        	processResult = javaExecutor.execute(jvmParameters, processOutputHandler);
		        } catch (Exception e) {
		    		processResult = getErrorResult(e);
		        	return Status.CANCEL_STATUS;
		        }
				return Status.OK_STATUS;
			}};
		job.setPriority(Job.LONG);
		final JavaProcess self = this;
        final IJobChangeListener listener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
				synchronized (self) {
					jobResult.set(processResult);
					actualResult.set(processResult);
				}
			}};
		job.addJobChangeListener(listener);
	}

	/**
	 * Start process
	 */
	public void start() {
		job.schedule();
	}

	/**
	 * Attempt to cancel job
	 */
	public void shutdown() {
		job.cancel();
	}
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("hashcode", hashCode())
                .add("\nprocess", job.getName())
                .toString();
    }
 
    /**
     * Returns process result for given exception
     * @param e Exception 
     * @return ProcessResult object
     */
    private ProcessResult getErrorResult(Exception e) {
    	return new ProcessResult() {

			@Override
			public ProcessResult assertNormalExitValue() throws ProcessException {
	            throw new ProcessException(String.format("Process '%s' crashed", job.getName()), e);
			}

			@Override
			public int getExitValue() {
				return -1;
			}

			@Override
			public ProcessResult rethrowFailure() throws ProcessException {
	            throw new ProcessException(String.format("Process '%s' crashed", job.getName()), e);
			}};
    }
}
