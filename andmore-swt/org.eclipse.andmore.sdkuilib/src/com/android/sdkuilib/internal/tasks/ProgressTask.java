/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdkuilib.internal.tasks;

import com.android.sdkuilib.internal.repository.ITask;
import com.android.sdkuilib.internal.repository.ITaskMonitor;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.swt.widgets.Shell;


/**
 * An {@link ITaskMonitor} that displays a {@link ProgressTaskDialog}.
 */
public final class ProgressTask extends TaskMonitorImpl {

    private final String mTitle;
    private final ProgressTaskDialog mDialog;
    private volatile boolean mAutoClose = true;


    /**
     * Creates a new {@link ProgressTask} with the given title.
     * This does NOT start the task. The caller must invoke {@link #start(ITask)}.
     */
    public ProgressTask(Shell parent, String title) {
        super(new ProgressTaskDialog(parent));
        mTitle = title;
        mDialog = (ProgressTaskDialog) getUiProvider();
        mDialog.setText(mTitle);
    }

    /**
     * Execute the given task asynchronously in a separate thread (not the UI thread).
     * The {@link ProgressTask} must not be reused after this call.
     * Returns when dialog is closed
     * @param task The task to be executed
     * @param onTerminateTask Task to run on termination
     */
    public void start(ITask task, Runnable onTerminateTask) {
        assert mDialog != null;
        Job job = createJob(mTitle, task);
        job.setPriority(Job.INTERACTIVE);
        if (onTerminateTask != null) {
        	job.addJobChangeListener(new JobChangeAdapter(){
	            @Override
	            public void done(IJobChangeEvent event) {
	            	onTerminateTask.run();
	            }
	        });
        }
        mDialog.open(job);
    }

    /**
     * Changes the auto-close behavior of the dialog on task completion.
     *
     * @param autoClose True if the dialog should be closed automatically when the task
     *   has completed.
     */
    public void setAutoClose(boolean autoClose) {
        if (autoClose != mAutoClose) {
            if (autoClose) {
                mDialog.setAutoCloseRequested();
            } else {
                mDialog.setManualCloseRequested();
            }
            mAutoClose = autoClose;
        }
    }

    /**
     * Creates a Job to run the task. The caller must call schedule() on the job to start it.
     * When the task completes, requests to close the dialog.
     * @return A new job that will run the task. The thread has not been started yet.
     */
    private Job createJob(String title, final ITask task) {
        if (task != null) {
            return new Job(title) {
                @Override
                protected IStatus run(IProgressMonitor m) {
                	IStatus status = Status.CANCEL_STATUS;
                	try {
                        task.run(ProgressTask.this);
                        if (mAutoClose) {
                            mDialog.setAutoCloseRequested();
                        } else {
                            mDialog.setManualCloseRequested();
                        }
                        status = Status.OK_STATUS;
                	} catch (Exception e) {
                		StringWriter builder = new StringWriter();
                        builder.append(e.getMessage()).append("\n");
                        PrintWriter writer = new PrintWriter(builder);
                        e.printStackTrace(writer);
                        logError(builder.toString());
                	} finally {
                	}
                    return status;
                }
            };
        }
        return null;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Sets the dialog to not auto-close since we want the user to see the error
     * (this is equivalent to calling {@code setAutoClose(false)}).
     */
    @Override
    public void logError(String format, Object...args) {
        setAutoClose(false);
        super.logError(format, args);
    }
}
