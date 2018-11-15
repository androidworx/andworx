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
import java.nio.charset.Charset;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.AndroidPrintStream;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.sdk.Sdk;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.launching.DefaultProjectClasspathEntry;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

import com.android.sdklib.BuildToolInfo;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * Factory to provide objects for ToolChain. Supports testing with mocks and stubs.
 *
 */
public class BuildFactory implements WorkbenchFactory {

	/** Java project - @see JavaCore */
	private IJavaProject javaProject;
	/** Standard output log stream. Defaults to System.out. */
	private AndroidPrintStream outStream;
	/** Error output log stream. Defaults to System.err */
	private AndroidPrintStream errStream;

	/**
	 * Construct BuildFactory object
	 * @param project Java project object
	 */
	public BuildFactory(IJavaProject javaProjec) {
		this.javaProject = javaProjec;
	}

	/** 
	 * Returns current Java project
	 * @return IJavaProject object 
	 */
	@Override
	public IJavaProject getJavaProject() {
		return javaProject;
	}

	/**
	 * Returns Debug Plugin instance
	 * @return DebugPlugin object
	 */
	@Override
	public DebugPlugin getDefaultDebugPlugin() {
		return DebugPlugin.getDefault();
	}

    /**
     * Returns JDT Configuration launcher
     * @return Launcher object
     */
	@Override
	public Launcher getLauncher() {
		return new Launcher(){
            private DebugEventListener listener;
            
			@Override
			public int launch(ILaunchConfiguration config) throws DebugException, CoreException {
		        config.launch(ILaunchManager.RUN_MODE, null);
		        listener = new DebugEventListener(config);
	                DebugPlugin.getDefault().addDebugEventListener(listener);
	                boolean doWait = true;
	                while (doWait) {
	                	// wait only up to 1 second to allow thread to be resheduled
		                synchronized(listener) {
		                    try {
		                        listener.wait(1000);
		                        doWait = !listener.isProcessTerminated();
		                    }   catch (InterruptedException e) {
		                    	break;
		                    }
		                }
	                }
		        int returnCode = -1;
	            if (listener.getProcess() != null) { // Check for null in case of interrupt terminating wait
	                returnCode = listener.getProcess().getExitValue();
	                String consoleOutput = listener.getConsoleOutput();
	                String consoleError = listener.getConsoleError();
	                if (!consoleOutput.isEmpty())
	                	AndmoreAndroidPlugin.printToConsole(javaProject.getElementName(), consoleOutput);
	                	
	                if (!consoleError.isEmpty())
	                	AndmoreAndroidPlugin.printErrorToConsole(javaProject.getElementName(), consoleError);
	            }
		        return returnCode;
			}

		};
	}

    /**
     * Returns build tools information for version assigned to project
     * @param projectState Project state includes configuration attributes and SDK references
     * @return BuildToolInfo object
     */
	@Override
    public BuildToolInfo getBuildToolInfo(ProjectState projectState) {
        BuildToolInfo buildToolInfo = projectState.getBuildToolInfo();
        if (buildToolInfo == null) {
    	    Sdk currentSdk = Sdk.getCurrent();
    	    if (currentSdk == null) // This is not expected
                throw new RuntimeException("No SDK available");
         	buildToolInfo = currentSdk.getLatestBuildTool();
         	if (buildToolInfo == null) // This is not expected 
         		throw new RuntimeException("No SDK build tool info available");
        	projectState.setBuildToolInfo(buildToolInfo);
        }
        return buildToolInfo;
    }
    
    /**
     * Returns XML classpath reference for given path
     * @param path The full path descriptor
     * @return momento
     * @throws CoreException
     */
   /**
     * Returns XML classpath reference for given path
     * @param path The full path descriptor
     * @return momento
     * @throws CoreException
     */
	@Override
	public String getMomento(Path path) throws CoreException {
		return JavaRuntime.newArchiveRuntimeClasspathEntry(path).getMemento();
	}

    /** 
     * Returns XML JRE container reference
     * @return momento
     * @throws CoreException
     */
	@Override
	public String getJreMemento() throws CoreException {
		IRuntimeClasspathEntry container = JavaRuntime.newRuntimeContainerClasspathEntry(
			new Path( JavaRuntime.JRE_CONTAINER ), IRuntimeClasspathEntry.STANDARD_CLASSES, javaProject);
		return container.getMemento();
	}

	/**
	 * Returns XML Default Project Classpath reference
	 * @return momento
	 * @throws CoreException
	 */
	@Override
	public String getDefaulClasspathMemento() throws CoreException {
		DefaultProjectClasspathEntry defaultClasspath = new DefaultProjectClasspathEntry(javaProject);
		return defaultClasspath.getMemento();
	}

	/**
	 * Returns standard log stream
	 * @return AndroidPrintStream object
	 */
	@Override
	public AndroidPrintStream getOutStream() {
		if (outStream == null)
			outStream = new AndroidPrintStream(javaProject.getElementName(), null, System.out);
		return outStream;
	}

	/**
	 * Sets standard log stream
	 * @param outStream AndroidPrintStream object
	 */
	@Override
	public void setOutStream(AndroidPrintStream outStream) {
		this.outStream = outStream;
	}

	/**
	 * Returns error log stream
	 * @return AndroidPrintStream object
	 */
	@Override
	public AndroidPrintStream getErrStream() {
		if (errStream == null)
			errStream = new AndroidPrintStream(javaProject.getElementName(), null, System.err);
		return errStream;
	}

	/**
	 * Sets error log stream
	 * @param outStream AndroidPrintStream object
	 */
	@Override
	public void setErrStream(AndroidPrintStream errStream) {
		this.errStream = errStream;
	}

	/**
	 * Returns current Android output folder - the "bin" project folder.
	 * @return IFolder object
	 */
	@Override
	public IFolder getAndroidOutputFolder() {
		return BaseProjectHelper.getAndroidOutputFolder(javaProject.getProject());
	}

	/** 
	 * Returns hashed version of given filepath
	 * @param file File to hash
	 * @return String
	 */
	@SuppressWarnings("deprecation")
	@Override
	public String getHashCode(File file) {
        // add a hash of the original file path
		HashFunction hashFunction = Hashing.md5();
        HashCode hashCode = hashFunction.hashString(file.getAbsolutePath(), Charset.defaultCharset());
		return hashCode.toString();
	}

	/**
	 * Return JDT launch configuration Java version
	 * @param configName  Name of launch configuration
	 * @return Java version in text format
	 * @throws CoreException
	 */
	@Override
	public String getJavaVersion(String configName) throws CoreException {
        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType launchConfigType = launchManager.getLaunchConfigurationType(
             IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        ILaunchConfigurationWorkingCopy wc = launchConfigType.newInstance(null, configName);
        ILaunchConfiguration launchConfiguration = wc.getOriginal();
        if (launchConfiguration == null) {
            wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, javaProject.getElementName());
       	    launchConfiguration = wc.doSave();
        }
    	IVMInstall vmInstall = JavaRuntime.computeVMInstall(launchConfiguration);
    	return ((AbstractVMInstall)vmInstall).getJavaVersion();
    }
}
