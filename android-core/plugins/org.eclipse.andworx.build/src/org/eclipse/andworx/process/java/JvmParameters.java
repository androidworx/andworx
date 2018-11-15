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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;

/**
 * Parameters required to launch a JVM.
 * Combines static launch configuration with dynamic program arguments and classpath
 */
public class JvmParameters {

	/** JDT Launch configuration */
	private ILaunchConfiguration configuration; 
	/** Aarguments passed to main method */
	private List<String> args; 
	/** Classpath */
	private List<String> classpath; 
	/** Monitor */
	private IProgressMonitor monitor;

	/**
	 * Construct JvmParameters object
	 * @param configuration JDT Launch configuration providing static parameters
	 */
	public JvmParameters(ILaunchConfiguration configuration) {
		this.configuration = configuration;
	}

	/**
	 * Returns program arguments
	 * @return argument list
	 */
	public List<String> getArgs() {
		if (args == null)
			args = new ArrayList<>();
		return args;
	}

	/**
	 * Set program arguments
	 * @param args Argument list
	 */
	public void setArgs(List<String> args) {
		this.args = args;
	}

	/**
	 * Returns classpath
	 * @return classpath as list
	 */
	public List<String> getClasspath() {
		if (classpath == null)
			classpath = new ArrayList<>();
		return classpath;
	}

	/**
	 * Set classpath
	 * @param classpath Classpath as list
	 */
	public void setClasspath(List<String> classpath) {
		this.classpath = classpath;
	}

	/**
	 * Returns progress monitor
	 * @return IProgressMonitor object
	 */
	public IProgressMonitor getMonitor() {
		if (monitor == null)
			monitor = new NullProgressMonitor();
		return monitor;
	}

	/**
	 * Set progress monitor
	 * @param monitor progress monitor
	 */
	public void setMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	/**
	 * Returns JDT launch configuration
	 * @return ILaunchConfiguration object
	 */
	public ILaunchConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Add given paths to classpath
	 * @param paths Path list
	 */
	public void pathsToClasspath(List<Path> paths) {
		classpath = getClasspath();
		classpath.forEach(p -> classpath.add(p.toString()));
	}
}
