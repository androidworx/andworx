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
/**
 * 
 */
package com.android.sdkuilib.internal.tasks;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.android.repository.api.DelegatingProgressIndicator;
import com.android.repository.api.ProgressIndicator;
import com.android.sdkuilib.internal.repository.ITaskMonitor;

/**
 * @author Andrew Bowley
 *
 * 12-11-2017
 */
public class SdkProgressIndicator implements ProgressIndicator {
	// Use Monitor max count value convert fractions to integer values
    private static final int MAX_COUNT = 10000;

	private final ITaskMonitor monitor;
    private volatile boolean isCancelled = false;
    private volatile boolean cancellable = true;
    // TODO - implement indeterminate cursor
    private volatile boolean indeterminate = false;
	
	/**
	 * 
	 */
	public SdkProgressIndicator(ITaskMonitor monitor) {
		this.monitor = monitor;
		monitor.setProgressMax(MAX_COUNT);
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#cancel()
	 */
	@Override
	public synchronized void  cancel() {
		if (cancellable)
			// TODO - Consider informing user request denied or disable stop button when not cancellable
			isCancelled = true;
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#getFraction()
	 */
	@Override
	public synchronized double getFraction() {
		return monitor.getProgress() / MAX_COUNT;
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#isCanceled()
	 */
	@Override
	public synchronized boolean isCanceled() {
		return isCancelled || monitor.isCancelRequested();
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#isCancellable()
	 */
	@Override
	public synchronized boolean isCancellable() {
		return cancellable;
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#isIndeterminate()
	 */
	@Override
	public synchronized boolean isIndeterminate() {
		return indeterminate;
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#logError(java.lang.String)
	 */
	@Override
	public synchronized void logError(String message) {
		monitor.logError(message);
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#logError(java.lang.String, java.lang.Throwable)
	 */
	@Override
	public synchronized void logError(String message, Throwable throwable) {
		monitor.error(throwable, message);
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#logInfo(java.lang.String)
	 */
	@Override
	public synchronized void logInfo(String message) {
		monitor.info(message);
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#logWarning(java.lang.String)
	 */
	@Override
	public synchronized void logWarning(String message) {
		monitor.warning(message);
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#logWarning(java.lang.String, java.lang.Throwable)
	 */
	@Override
	public synchronized void logWarning(String message, Throwable throwable) {
		StringWriter builder = new StringWriter();
		builder.append(message);
		if (throwable != null) {
	        builder.append("\n");
            PrintWriter writer = new PrintWriter(builder);
            throwable.printStackTrace(writer);
		}
		monitor.warning(builder.toString());
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#setCancellable(boolean)
	 */
	@Override
	public synchronized void setCancellable(boolean cancellable) {
		this.cancellable = cancellable;
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#setFraction(double)
	 */
	@Override
	public synchronized void setFraction(double fraction) {
		int progress = fraction == 1.0 ? MAX_COUNT : (int)((double)MAX_COUNT * fraction);
		monitor.incProgress(progress - monitor.getProgress());
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#setIndeterminate(boolean)
	 */
	@Override
	public synchronized void setIndeterminate(boolean indeterminate) {
		this.indeterminate = indeterminate;
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#setSecondaryText(java.lang.String)
	 */
	@Override
	public synchronized void setSecondaryText(String text) {
		// TODO - implement secondary text
		monitor.logVerbose(text);
	}

	/* (non-Javadoc)
	 * @see com.android.repository.api.ProgressIndicator#setText(java.lang.String)
	 */
	@Override
	public synchronized void setText(String text) {
		monitor.setDescription(text);
	}

    /**
     * Creates a new progress indicator that just delegates to {@code this}, except that its
     * {@code [0, 1]} progress interval maps to {@code this}'s {@code [getFraction()-max]}.
     * So for example if {@code this} is currently at 10% and a sub progress is created with
     * {@code max = 0.5}, when the sub progress is at 50% the parent progress will be at 30%, and
     * when the sub progress is at 100% the parent will be at 50%.
     */
	@Override
	public ProgressIndicator createSubProgress(double max) {
        double start = getFraction();
        
        // Unfortunately some dummy indicators always report their fraction as 1. In that case just
        // return the indicator itself.
        if (start == 1) {
            return this;
        }

        double subRange = max - start;
        // Check that we're at least close to being a valid value. If we're equal to or less than 0
        // we'll treat it as 0 (that is, sets do nothing and gets just return the 0).
        if (subRange < -0.0001 || subRange > 1.0001) {
            //logError("Progress subrange out of bounds: " + subRange);
        }

        return new DelegatingProgressIndicator(this) {
            @Override
            public void setFraction(double subFraction) {
                if (subRange > 0) {
                    subFraction = Math.min(1, Math.max(0, subFraction));
                    super.setFraction(start + subFraction * subRange);
                }
            }

            @Override
            public double getFraction() {
                return Math.min(
                        1,
                        Math.max(0, subRange > 0 ? (super.getFraction() - start) / subRange : 0));
            }
        };
    }
	
}
