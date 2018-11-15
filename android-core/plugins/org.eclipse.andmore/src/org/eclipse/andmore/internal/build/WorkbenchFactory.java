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
package org.eclipse.andmore.internal.build;

import java.io.File;

import org.eclipse.andmore.AndroidPrintStream;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;

import com.android.sdklib.BuildToolInfo;

import org.eclipse.debug.core.DebugPlugin;

/**
 * Interface for factory to provide objects for ToolChain. Supports testing with mocks and stubs.
 */
public interface WorkbenchFactory {
	/** 
	 * Returns current Java project
	 * @return IJavaProject object 
	 */
	IJavaProject getJavaProject();
	/**
	 * Returns current Android output folder - the "bin" project folder.
	 * @return IFolder object
	 */
	IFolder getAndroidOutputFolder();
	/**
	 * Returns Debug Plugin instance
	 * @return DebugPlugin object
	 */
    DebugPlugin getDefaultDebugPlugin();
    /**
     * Returns JDT Configuration launcher
     * @return Launcher object
     */
    Launcher getLauncher();
    /**
     * Returns build tools information for version assigned to project
     * @param projectState Project state includes configuration attributes and SDK references
     * @return BuildToolInfo object
     */
    BuildToolInfo getBuildToolInfo(ProjectState projectState);
    /**
     * Returns XML classpath reference for given path
     * @param path The full path descriptor
     * @return momento
     * @throws CoreException
     */
    String getMomento(Path path) throws CoreException;
    /** 
     * Returns XML JRE container reference
     * @return momento
     * @throws CoreException
     */
	String getJreMemento() throws CoreException;
	// new javaProject)
	/**
	 * Returns XML Default Project Classpath reference
	 * @return momento
	 * @throws CoreException
	 */
	String getDefaulClasspathMemento() throws CoreException;
	/**
	 * Returns standard log stream
	 * @return AndroidPrintStream object
	 */
	AndroidPrintStream getOutStream();
	/**
	 * Sets standard log stream
	 * @param outStream AndroidPrintStream object
	 */
	void setOutStream(AndroidPrintStream outStream);
	/**
	 * Returns error log stream
	 * @return AndroidPrintStream object
	 */
	AndroidPrintStream getErrStream();
	/**
	 * Sets error log stream
	 * @param outStream AndroidPrintStream object
	 */
	void setErrStream(AndroidPrintStream errStream);
	/** 
	 * Returns hashed version of given filepath
	 * @param file File to hash
	 * @return String
	 */
	String getHashCode(File file);
	/**
	 * Return JDT launch configuration Java version
	 * @param configName  Name of launch configuration
	 * @return Java version in text format
	 * @throws CoreException
	 */
	String getJavaVersion(String configName) throws CoreException;
}
