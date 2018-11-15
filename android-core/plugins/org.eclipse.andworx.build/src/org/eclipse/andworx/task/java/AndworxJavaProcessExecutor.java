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
package org.eclipse.andworx.task.java;

import java.io.IOException;

import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.process.java.AndworxJavaLaunchDelegate;
import org.eclipse.andworx.process.java.JvmParameters;

import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.utils.ILogger;

/**
 * Process executor that uses Eclipse JDT to execute external java processes.
 */
public class AndworxJavaProcessExecutor {
     private static ILogger logger = SdkLogger.getLogger(AndworxJavaProcessExecutor.class.getName());
    
	public AndworxJavaProcessExecutor() {
		super();
	}

	/**
	 * Launch JVM and return exit code on process completion
	 * @param jvmParameters Launch input parameters
	 * @param processOutputHandler Handler to grab process output
	 * @return ProcessResult object
	 */
	public ProcessResult execute(JvmParameters jvmParameters, ProcessOutputHandler processOutputHandler) {
	    ProcessOutput output = processOutputHandler.createOutput();
        ProcessException caught = null;
        int result = -1;
        // Delegate JVM launch. This class is just an adapter to control output handling and create the return object
        try {
    	    AndworxJavaLaunchDelegate launchDelegate = new AndworxJavaLaunchDelegate();
			result = launchDelegate.run(jvmParameters, output);
        } catch (ProcessException e) {
        	caught = e;
		} finally {
            try {
                output.close();
            } catch (IOException e) {
            	logger.warning("Exception while closing sub process streams", e);
            }
        }
        int exitValue = result;
        ProcessException failure = caught;
        String name = jvmParameters.getConfiguration().getName();
		ProcessResult processResult = new ProcessResult() {

			@Override
			public ProcessResult assertNormalExitValue() throws ProcessException {
	            if (exitValue != 0) {
	                throw new ProcessException(String.format("Process '%s' finished with non-zero exit value %d", name, exitValue));
	            }
				return this;
			}

			@Override
			public int getExitValue() {
				return exitValue;
			}

			@Override
			public ProcessResult rethrowFailure() throws ProcessException {
	            if (failure != null) {
	                throw failure;
	            }
				return this;
			}};
        try {
            processOutputHandler.handleOutput(output);
        } catch (ProcessException e) {
            logger.warning("Process output error: %s", e.getMessage());
        }
		return processResult;
	}

}
