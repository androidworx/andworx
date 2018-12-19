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
package org.eclipse.andmore.base;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Jobs are units of runnable work that can be scheduled to be run with the job
 * manager.  Once a job has completed, it can be scheduled to run again (jobs are
 * reusable).
 */
public class AndworxJob {

	private final String name;
	private final Job job;
	private final IJobFunction jobFunction;
	/** The list of job listeners.*/
	private ListenerList<IJobChangeListener> listeners;
	
	public AndworxJob(String name, IJobFunction jobFunction) {
		this.name = name;
		this.jobFunction = jobFunction;
		if (isOsgiPlatform())
			job = Job.create(name, jobFunction);
		else
			job = null;
	}

	public String getName() {
		return name;
	}

	public IJobFunction getJobFunction() {
		return jobFunction;
	}

	public ListenerList<IJobChangeListener> getlisteners() {
		if (listeners != null)
			return listeners;
		else
			return new ListenerList<>();
	}
	
	/**
	 * Registers a job listener with this job
	 * Has no effect if an identical listener is already registered.
	 *
	 * @param listener the listener to be added.
	 */
	public final void addJobChangeListener(IJobChangeListener listener) {
		if (isOsgiPlatform())
			job.addJobChangeListener(listener);
		else {
			if (listeners == null)
				listeners = new ListenerList<>(ListenerList.IDENTITY);
			listeners.add(listener);
		}
			
	}

	/**
	 * Schedules this job to be run.  The job is added to a queue of waiting
	 * jobs, and will be run when it arrives at the beginning of the queue.
	 */
	public void schedule() {
		if (isOsgiPlatform())
		    job.schedule(0L);
		else {
			IStatus status = jobFunction.run(new NullProgressMonitor());
			IJobChangeEvent event = new IJobChangeEvent() {

				@Override
				public long getDelay() {
					return -1;
				}

				@Override
				public Job getJob() {
					return null;
				}

				@Override
				public IStatus getResult() {
					return status;
				}

				@Override
				public IStatus getJobGroupResult() {
					return null;
				}};
			for (IJobChangeListener listener: getlisteners())
				listener.done(event);
		}
	}

    private static boolean isOsgiPlatform() {
    	return (BasePlugin.instance() != null);
    }
}
