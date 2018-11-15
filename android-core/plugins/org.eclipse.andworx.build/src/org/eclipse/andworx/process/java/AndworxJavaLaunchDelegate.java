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
package org.eclipse.andworx.process.java;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.andworx.helper.DebugEventListener;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.internal.launching.LaunchingMessages;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.osgi.util.NLS;

import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;

/**
 * Java launch delegate applies launch parameters to append to those in the launch configuration
 */
public class AndworxJavaLaunchDelegate extends JavaLaunchDelegate {

	/** Synchronization required around classpath code as it is not thread safe */
	private static final Object LOCK = new Object();

	/**
	 * Construct AndworxJavaLaunchDelegate object
	 */
	public AndworxJavaLaunchDelegate() {
		super();
	}

	/**
	 * Launch JVM sychronously and return exit code
	 * @param jvmParameters Launch input parameters, including LaunchConfiguration object
	 * @param output Process output grabber
	 * @return Process exit value or -1 if an error occurs
	 * @throws ProcessException
	 */
	public int run(JvmParameters jvmParameters, ProcessOutput output) throws ProcessException {
		ILaunchConfiguration configuration = jvmParameters.getConfiguration();
		String mode = org.eclipse.debug.core.ILaunchManager.RUN_MODE;
		IProgressMonitor monitor = jvmParameters.getMonitor();
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		DebugEventListener listener = null;
		try {
			// Create a Virtual Machine runner configuration blending static configuration with dynamic parameters
			VMRunnerConfiguration vmRunnerConfig = configureLaunch(jvmParameters, mode, monitor);
			if (vmRunnerConfig == null)
				return -1;
			// Launch JVM with listener primed to capture process creation event.
			// The listener also notifies process termination 
			IVMRunner runner = getVMRunner(configuration, mode);
			ILaunch launch = getLaunch(configuration, mode);
			// Event listener selt-registers with Debug plugin 
			listener = new DebugEventListener(launch, output);
			runner.run(vmRunnerConfig, launch, monitor);
            synchronized(listener) {
                try {
                	listener.wait();
                 }   catch (InterruptedException e) {
                	Thread.interrupted();
                	return -1;
                 }
            }
            // Get process exit value, if available
            IProcess process = listener.getProcess();
	        // Get return code
		    int returnCode = -1;
            if ((process != null) && process.isTerminated())
            	returnCode = process.getExitValue();
		    return returnCode;
		} catch (CoreException e) {
			throw new ProcessException("Error launching " + configuration.getName(), e);
		} finally {
			if (listener != null) // Unregister listener
				DebugPlugin.getDefault().removeDebugEventListener(listener);
		}
	}

	/**
	 * Create a Virtual Machine runner configuration blending static configuration with dynamic parameters
	 * @param jvmParameters Launch input parameters, including LaunchConfiguration object
	 * @param mode Run mode - always "run"
	 * @param monitor Progress monitor
	 * @return VMRunnerConfiguration object
	 * @throws CoreException
	 */
	private VMRunnerConfiguration configureLaunch(JvmParameters jvmParameters, String mode, IProgressMonitor monitor) throws CoreException {
		VMRunnerConfiguration runConfig = null;
		ILaunchConfiguration configuration = jvmParameters.getConfiguration();
		
		monitor.beginTask(NLS.bind("{0}...", new String[]{configuration.getName()}), 3); //$NON-NLS-1$
		// Check for cancellation
		if (monitor.isCanceled()) {
			return null;
		}
		try {
			monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Verifying_launch_attributes____1); 
							
			String mainTypeName = verifyMainTypeName(configuration);
	
			File workingDir = verifyWorkingDirectory(configuration);
			String workingDirName = null;
			if (workingDir != null) {
				workingDirName = workingDir.getAbsolutePath();
			}
			
			// Environment variables
			String[] envp = getEnvironment(configuration);
			
			// Program & VM arguments
			String pgmArgs = getProgramArguments(configuration);
	        StringBuilder argsString = new StringBuilder();
			if ((pgmArgs != null) && (!pgmArgs.isEmpty())) {
				argsString.append(pgmArgs);
			}
	        Iterator<String> iterator = jvmParameters.getArgs().iterator();
	        if (iterator.hasNext()) {
	        	if (argsString.length() > 0)
	        		argsString.append(' ');
	        	argsString.append(iterator.next());
	        	while (iterator.hasNext())
	        		argsString.append(' ').append(iterator.next());
	        }
	        pgmArgs = argsString.toString();
			String vmArgs = getVMArguments(configuration);
			ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);
			
			// VM-specific attributes
			Map<String, Object> vmAttributesMap = null;
			String[] configClasspath = null;
			// Monitor block required to overcome lack of thread safety
			synchronized(LOCK) {
				try {
					vmAttributesMap = getVMSpecificAttributesMap(configuration);
				} catch (NullPointerException e) {
					// Error unfathomable
				}
				if (vmAttributesMap == null)
					vmAttributesMap = Collections.emptyMap();
				configClasspath = getClasspath(configuration);
				// Classpath
				List<String> classpath = jvmParameters.getClasspath();
				if ((configClasspath != null) && ! (configClasspath.length == 0)) {
					List<String> appendedClasspath = new ArrayList<>(configClasspath.length + classpath.size());
					appendedClasspath.addAll(Arrays.asList(configClasspath));
					appendedClasspath.addAll(classpath);
					classpath = appendedClasspath;
				}
				String[] classpathArray = classpath.toArray(new String[classpath.size()]);
				// Create VM config
				runConfig = new VMRunnerConfiguration(mainTypeName, classpathArray);
				runConfig.setProgramArguments(execArgs.getProgramArgumentsArray());
				runConfig.setEnvironment(envp);
				runConfig.setVMArguments(execArgs.getVMArgumentsArray());
				runConfig.setWorkingDirectory(workingDirName);
				runConfig.setVMSpecificAttributesMap(vmAttributesMap);
		
				// Bootpath
				runConfig.setBootClassPath(getBootpath(configuration));
				
				// Check for cancellation
				if (monitor.isCanceled()) {
					return null;
				}		
				// done the verification phase
				monitor.worked(1);
			}
			return runConfig;
		}
		finally {
			monitor.done();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#getLaunch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	@Override
	public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
		Launch launch = new Launch(configuration, mode, null);
		// Following code from LaunchConfiguration.launch()
		// Set timestamp
		launch.setAttribute(DebugPlugin.ATTR_LAUNCH_TIMESTAMP, Long.toString(System.currentTimeMillis()));
		// Set capture output
		boolean captureOutput = configuration.getAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, true);
		if (!captureOutput) {
		    launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, "false"); //$NON-NLS-1$
		} else {
		    launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, null);
		}
		// Set console encoding
		launch.setAttribute(DebugPlugin.ATTR_CONSOLE_ENCODING, getLaunchManager().getEncoding(configuration));
		return launch;
	}

}
