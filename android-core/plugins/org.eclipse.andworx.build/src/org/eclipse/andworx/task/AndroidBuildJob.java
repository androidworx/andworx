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

import java.util.concurrent.Future;

import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;

import com.android.annotations.NonNull;

/**
 * Atomic piece of work for a build which is run as a Job.
 * A BuildTask object passed to the construction performs the work.
 * Abstract method startTask() must be overriden to launch the task.
 * This allows parameters to be passed to the task object, if required.
 * The task must return a Future object so that in the case the task fails, 
 * the Future provides the exception which caused the failure.
 */
public class AndroidBuildJob {
	/** Name of task - identify task in messages */
	private String taskName;
	/** The job in which the task is performed */
	private Job job;
	/** Job completion status - null prior to completions */
	private volatile IStatus status = Status.CANCEL_STATUS;

	/**
	 * Construct AndroidBuildJob object
	 * @param buildTask Object to perfom unit of work
	 */
	protected AndroidBuildJob(@NonNull BuildTask buildTask) {
		this.taskName = buildTask.getTaskName();
		// Create Job to schedule start of task
		job = new Job(taskName) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try { 
		        	Future<Void> future = buildTask.doFullTaskAction();
			        future.get();
		        } catch (Exception e) {
		        	AndworxBuildPlugin.instance().logAndPrintError(e, taskName, "Error in %s", getName());
		        	return Status.CANCEL_STATUS;
		        }
				return Status.OK_STATUS;
			}};
		// Anticipate task may take a while to run
		job.setPriority(Job.LONG);
		final AndroidBuildJob self = this;
		// Add job lisener to notify task completion, even if task fails with uncaught exception
        final IJobChangeListener listener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
				synchronized (self) {
					self.status = event.getResult();
					self.notifyAll();
				}
			}};
		job.addJobChangeListener(listener);
	}

	/**
	 * Returns task name
	 * @return String
	 */
	public String getName() {
		return taskName;
	}

	/**
	 * Schedule task to start
	 */
	public void schedule() {
		job.schedule();;
	}

	/**
	 * Cancel job if not already running
	 * @return flag set false if job already running when cancel requested
	 */
	public boolean cancel() {
		return job.cancel();
	}

	/**
	 * Returns job status
	 * @return OK_STATUS if task completed successfully, otherwise CANCEL_STATUS
	 */
	public IStatus getStatus() {
		return status;
	}

}
