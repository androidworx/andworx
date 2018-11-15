/*
 * Copyright (C) 2011 The Android Open Source Project
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.android.sdkuilib.internal.repository.ITask;
import com.android.sdkuilib.internal.repository.ITaskMonitor;
import com.android.sdkuilib.internal.repository.UserCredentials;
import com.android.sdkuilib.ui.AuthenticationDialog;
import com.android.sdkuilib.ui.GridDialog;


/**
 * Implements a "view" that uses an existing progress bar, status button and
 * status text to display a {@link ITaskMonitor}.
 */
public final class ProgressView implements IProgressUiProvider, ITaskMonitor {

    private static enum State {
        /** View created but there's no task running. Next state can only be ACTIVE. */
        IDLE,
        /** A task is currently running. Next state is either STOP_PENDING or IDLE. */
        ACTIVE,
        /** Stop button has been clicked. Waiting for thread to finish. Next state is IDLE. */
        STOP_PENDING,
    }

    /** The current mode of operation of the dialog. */
    private State mState = State.IDLE;



    // UI fields
    private final Label mLabel;
    private final Control mStopButton;
    private final ProgressBar mProgressBar;

    /** Logger object. Cannot not be null. */
    private final ILogUiProvider mLog;
    private volatile ITaskMonitor taskMonitor;
    private volatile boolean isCanceled;

    /**
     * Creates a new {@link ProgressView} object, a simple "holder" for the various
     * widgets used to display and update a progress + status bar.
     *
     * @param label The label to display titles of status updates (e.g. task titles and
     *      calls to {@link #setDescription(String)}.) Must not be null.
     * @param progressBar The progress bar to update during a task. Must not be null.
     * @param stopButton The stop button. It will be disabled when there's no task that can
     *      be interrupted. A selection listener will be attached to it. Optional. Can be null.
     * @param log A <em>mandatory</em> logger object that will be used to report all the log.
     *      Must not be null.
     */
    public ProgressView(
            Label label,
            ProgressBar progressBar,
            Control stopButton,
            ILogUiProvider log) {
        mLabel = label;
        mProgressBar = progressBar;
        mLog = log;
        mProgressBar.setEnabled(false);

        mStopButton = stopButton;
        if (mStopButton != null) {
            mStopButton.addListener(SWT.Selection, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    if (mState == State.ACTIVE) {
                    	isCanceled = true;
                        changeState(State.STOP_PENDING);
                    }
                }
            });
        }
    }

    /**
     * Starts the task returns. Runs given callback when the task is either finished or canceled.
     */
    public void startAsyncTask(
            final String title,
            final ITask task, 
            final Runnable onTerminateTask) {
        // If for some reason the UI has been disposed, just abort the thread.
        if (mProgressBar.isDisposed())
            return;
        if (task != null) {
            try {
                syncExec(mProgressBar, new Runnable() {
                    @Override
                    public void run() {
                        mLabel.setText(title);
                        mProgressBar.setSelection(0);
                        mProgressBar.setEnabled(true);
                    }
                });
                changeState(ProgressView.State.ACTIVE);
                Job job = new Job(title) {
                    @Override
                    protected IStatus run(IProgressMonitor m) {
                    	IStatus status = Status.CANCEL_STATUS;
                    	try {
                    		taskMonitor = new TaskMonitorImpl(ProgressView.this); 
                            task.run(taskMonitor);
                            status = Status.OK_STATUS;
                    	} catch (Exception e) {
                    		StringWriter builder = new StringWriter();
                            builder.append(e.getMessage()).append("\n");
                            PrintWriter writer = new PrintWriter(builder);
                            e.printStackTrace(writer);
                            mLog.logError(builder.toString());
                    	} finally {
                    		taskMonitor = null;
                            endTask();
                    	}
                        return status;
                    }
                };
                job.setPriority(Job.INTERACTIVE);
                if (onTerminateTask != null) {
                    job.addJobChangeListener(new JobChangeAdapter() {
                        @Override
                        public void done(IJobChangeEvent event) {
                        	onTerminateTask.run();
                        }
                    });
                }
                job.schedule();
            } catch (Exception e) {
        		StringWriter builder = new StringWriter();
                builder.append(e.getMessage()).append("\n");
                PrintWriter writer = new PrintWriter(builder);
                e.printStackTrace(writer);
                mLog.logError(builder.toString());
            }
        }
    }

    /**
     * Starts the task and blocks until the task is completed or canceled.
     */
    public void startSyncTask(
            final String title,
            final ITask task) {
        // If for some reason the UI has been disposed, just abort the thread.
        if (mProgressBar.isDisposed())
            return;
        if (task != null) {
            try {
                syncExec(mProgressBar, new Runnable() {
                    @Override
                    public void run() {
                        mLabel.setText(title);
                        mProgressBar.setSelection(0);
                        mProgressBar.setEnabled(true);
                    }
                });
                changeState(ProgressView.State.ACTIVE);
                task.run(new TaskMonitorImpl(ProgressView.this));
            } catch (Exception e) {
        		StringWriter builder = new StringWriter();
                builder.append(e.getMessage()).append("\n");
                PrintWriter writer = new PrintWriter(builder);
                e.printStackTrace(writer);
                mLog.logError(builder.toString());
            }
            finally {
                endTask();
            }
        }
    }

   public void endTask()
    {
        changeState(ProgressView.State.IDLE);
        syncExec(mProgressBar, new Runnable() {
            @Override
            public void run() {
                mProgressBar.setSelection(0);
                mProgressBar.setEnabled(false);
            }
        });
    }
    
    public void syncExec(final Widget widget, final Runnable runnable) {
        if (widget != null && !widget.isDisposed()) {
            widget.getDisplay().syncExec(new Runnable() {
                @Override
                public void run() {
                    // Check again whether the widget got disposed between the time where
                    // we requested the syncExec and the time it actually happened.
                    if (!widget.isDisposed()) {
                        runnable.run();
                    }
                }
            });
        }
    }

    private void changeState(State state) {
        if (mState != null ) {
            mState = state;
        }

        syncExec(mStopButton, new Runnable() {
            @Override
            public void run() {
            	if (mState == State.ACTIVE)
            		mStopButton.setEnabled(true);
            }
        });

    }

    // --- Implementation of ITaskUiProvider ---

    @Override
    public boolean isCancelRequested() {
    	if (isCanceled)
    		log("Stop button pressed");
        return isCanceled;
    }

    /**
     * Sets the description in the current task dialog.
     * This method can be invoked from a non-UI thread.
     */
    @Override
    public void setDescription(final String description) {
        syncExec(mLabel, new Runnable() {
            @Override
            public void run() {
                mLabel.setText(description);
            }
        });

        mLog.setDescription(description);
    }

    /**
     * Logs a "normal" information line.
     * This method can be invoked from a non-UI thread.
     */
    @Override
    public void log(String log) {
        mLog.log(log);
    }

    /**
     * Logs an "error" information line.
     * This method can be invoked from a non-UI thread.
     */
    @Override
    public void logError(String log) {
        mLog.logError(log);
    }

    /**
     * Logs a "verbose" information line, that is extra details which are typically
     * not that useful for the end-user and might be hidden until explicitly shown.
     * This method can be invoked from a non-UI thread.
     */
    @Override
    public void logVerbose(String log) {
        mLog.logVerbose(log);
    }

    /**
     * Sets the max value of the progress bar.
     * This method can be invoked from a non-UI thread.
     *
     * @see ProgressBar#setMaximum(int)
     */
    @Override
    public void setProgressMax(final int max) {
        syncExec(mProgressBar, new Runnable() {
            @Override
            public void run() {
                mProgressBar.setMaximum(max);
            }
        });
    }

    /**
     * Sets the current value of the progress bar.
     * This method can be invoked from a non-UI thread.
     */
    @Override
    public void setProgress(final int value) {
        syncExec(mProgressBar, new Runnable() {
            @Override
            public void run() {
                mProgressBar.setSelection(value);
            }
        });
    }

    /**
     * Returns the current value of the progress bar,
     * between 0 and up to {@link #setProgressMax(int)} - 1.
     * This method can be invoked from a non-UI thread.
     */
    @Override
    public int getProgress() {
        final int[] result = new int[] { 0 };

        if (!mProgressBar.isDisposed()) {
            mProgressBar.getDisplay().syncExec(new Runnable() {
                @Override
                public void run() {
                    if (!mProgressBar.isDisposed()) {
                        result[0] = mProgressBar.getSelection();
                    }
                }
            });
        }

        return result[0];
    }

    @Override
    public boolean displayPrompt(final String title, final String message) {
        final boolean[] result = new boolean[] { false };

        syncExec(mProgressBar, new Runnable() {
            @Override
            public void run() {
                Shell shell = mProgressBar.getShell();
                result[0] = MessageDialog.openQuestion(shell, title, message);
            }
        });

        return result[0];
    }

    /**
     * Display info dialog box.
     *
     * This implementation allow this to be called from any thread, it
     * makes sure the dialog is opened synchronously in the ui thread.
     *
     * @param title The title of the dialog box
     * @param message The error message
     */
	@Override
	public void displayInfo(String title, String message) {

        syncExec(mProgressBar, new Runnable() {
            @Override
            public void run() {
                Shell shell = mProgressBar.getShell();
                MessageDialog.openInformation(shell, title, message);
            }
        });
	}

    /**
     * This method opens a pop-up window which requests for User Credentials.
     *
     * @param title The title of the window.
     * @param message The message to displayed in the login/password window.
     * @return Returns user provided credentials.
     *         If operation is <b>canceled</b> by user the return value must be <b>null</b>.
     * @see ITaskMonitor#displayLoginCredentialsPrompt(String, String)
     */
    @Override
    public UserCredentials
            displayLoginCredentialsPrompt(final String title, final String message) {
        final AtomicReference<UserCredentials> result = new AtomicReference<UserCredentials>(null);

        // open dialog and request login and password
        syncExec(mProgressBar, new Runnable() {
            @Override
            public void run() {
                Shell shell = mProgressBar.getShell();
                AuthenticationDialog authenticationDialog = new AuthenticationDialog(shell,
                        title,
                        message);
                int dlgResult = authenticationDialog.open();
                if (dlgResult == GridDialog.OK) {
                    result.set(new UserCredentials(
                        authenticationDialog.getLogin(),
                        authenticationDialog.getPassword(),
                        authenticationDialog.getWorkstation(),
                        authenticationDialog.getDomain()));
                }
            }
        });

        return result.get();
    }

	@Override
	public void error(Throwable t, String msgFormat, Object... args) {
		if (taskMonitor != null)
			taskMonitor.error(t, msgFormat, args);
	}

	@Override
	public void warning(String msgFormat, Object... args) {
		if (taskMonitor != null)
			taskMonitor.warning(msgFormat, args);
	}

	@Override
	public void info(String msgFormat, Object... args) {
		if (taskMonitor != null)
			taskMonitor.info(msgFormat, args);
	}

	@Override
	public void verbose(String msgFormat, Object... args) {
		if (taskMonitor != null)
			taskMonitor.verbose(msgFormat, args);
	}

	@Override
	public void setDescription(String format, Object... args) {
		if (taskMonitor != null)
			taskMonitor.setDescription(format, args);
	}

	@Override
	public void log(String format, Object... args) {
		if (taskMonitor != null)
			taskMonitor.log(format, args);
	}

	@Override
	public void logError(String format, Object... args) {
		if (taskMonitor != null)
			taskMonitor.logError(format, args);
	}

	@Override
	public void logVerbose(String format, Object... args) {
		if (taskMonitor != null)
			taskMonitor.logVerbose(format, args);
	}

	@Override
	public int getProgressMax() {
		if (taskMonitor != null)
			taskMonitor.getProgressMax();
		return 0;
	}

	@Override
	public void incProgress(int delta) {
		if (taskMonitor != null)
			taskMonitor.incProgress(delta);
	}

	@Override
	public ITaskMonitor createSubMonitor(int tickCount) {
		throw new java.lang.UnsupportedOperationException();
	}

	@Override
	public void cancel() {
		if (taskMonitor != null) {
			isCanceled = true;
			taskMonitor.cancel();
		}
	}

	@Override
	public void setCancellable(boolean cancellable) {
		if (taskMonitor != null)
			taskMonitor.setCancellable(cancellable);
	}

	@Override
	public boolean isCancellable() {
		if (taskMonitor != null)
			return taskMonitor.isCancellable();	
		return false;
	}

	@Override
	public void setIndeterminate(boolean indeterminate) {
		if (taskMonitor != null)
			taskMonitor.setIndeterminate(indeterminate);		
	}

	@Override
	public boolean isIndeterminate() {
		if (taskMonitor != null)
			return taskMonitor.isIndeterminate();
		return false;
	}

	@Override
	public void setFraction(double fraction) {
		if (taskMonitor != null)
			taskMonitor.setFraction(fraction);		
	}

	@Override
	public void reset() {
		if (taskMonitor != null)
			taskMonitor.reset();
	}
}

