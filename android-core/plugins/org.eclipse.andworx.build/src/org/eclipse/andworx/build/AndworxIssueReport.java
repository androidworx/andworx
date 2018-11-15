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
package org.eclipse.andworx.build;

import java.util.List;

import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.SyncIssue;
import com.android.utils.ILogger;

/**
* Reporter for issues during evaluation.
*
* This handles dealing with errors differently if the project is being run from the command line
* or from the IDE, in particular during Sync when we don't want to throw any exception
*/
public class AndworxIssueReport implements EvalIssueReporter {

	private final ILogger logger;
	
	public AndworxIssueReport(ILogger logger) {
		this.logger = logger;
	}

	public ILogger getLogger() {
		return logger;
	}
    /**
     * 
     * Reports an error.
     *
     * When running outside of IDE sync, this will throw and exception and abort execution.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a [SyncIssue] if the error is only recorded.
     */
	@Override
	public SyncIssue reportError(Type type, String msg) {
		return syncIssue(type, Severity.ERROR, msg, null);
	}


    /**
     * Reports an error.
     *
     * When running outside of IDE sync, this will throw and exception and abort execution.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @param data a data representing the source of the error. This goes hand in hand with the
     * <var>type</var>, and is not meant to be readable. Instead a (possibly translated) message
     * is created from this data and type.
     * @return a [SyncIssue] if the error is only recorded.
     */
	@Override
	public SyncIssue reportError(Type type, String msg, String data) {
		return syncIssue(type, Severity.ERROR, msg, data);
	}

	@Override
	public SyncIssue reportIssue(Type type, Severity severity, String msg) {
		return syncIssue(type, severity, msg, null);
	}

	@Override
	public SyncIssue reportIssue(Type type, Severity severity, String msg, String data) {
		return syncIssue(type, severity, msg, data);
	}

	@Override
	public SyncIssue reportWarning(Type type, String msg) {
		return syncIssue(type, Severity.WARNING, msg, null);
	}

	@Override
	public SyncIssue reportWarning(Type type, String msg, String data) {
		return syncIssue(type, Severity.WARNING, msg, data);
	}
	
	SyncIssue syncIssue(Type type, Severity severity, String msg, String data) {
		return new SyncIssue() {

			@Override
			public int getSeverity() {
				return severity.getSeverity();
			}

			@Override
			public int getType() {
				return type.getType();
			}

			@Override
			public String getData() {
				return data;
			}

			@Override
			public String getMessage() {
				return msg;
			}

			@Override
			public List<String> getMultiLineMessage() {
				return null;
			}};
	}
}
