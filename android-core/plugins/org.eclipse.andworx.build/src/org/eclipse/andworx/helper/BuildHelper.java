/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not FilenameFilter filter this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
  http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.helper;

import static org.eclipse.andworx.AndworxConstants.CONTAINER_DEPENDENCIES;
import static org.eclipse.andworx.AndworxConstants.NATURE_DEFAULT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.andworx.build.BuildElement;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.launching.DefaultProjectClasspathEntry;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.process.JavaProcessInfo;
import com.google.common.io.MoreFiles;
import com.android.utils.FileUtils;

/**
 * Utility to facilitate project builds
 */
public class BuildHelper {

	static private final String NO_LOCATE_ITEM = "Could not locate '%1$s'. This will not be added to the package";
	static private SdkLogger logger = SdkLogger.getLogger(BuildHelper.class.getName());
	
	private final BuildElementFactory elementRactory;

	/**
	 * Construct BuildHelper object
	 * @param buildElementFactory
	 */
	public BuildHelper(BuildElementFactory elementFactory) {
		this.elementRactory = elementFactory;
	}
	
    /**
     * Prepare build directory by creating it if does not exist, otherwise clean it
     * @param directory Directory to prepare
     * @throws IOException 
    */
    public void prepareDir(@Nullable File directory) throws IOException {
    	if (directory == null)
    		return;
    	if (!directory.exists())
    		FileUtils.mkdirs(directory);
    	else { // FileUtils unreliable at deleting directory contenets
    		MoreFiles.deleteDirectoryContents(directory.toPath());
    	}
    }

    /**
     * Creates build element for a build output, saves it and returns it
     * @param outputType OutputType enum
     * @param outputFile The output file
     * @param apkData APK information to include in build element
     * @return BuildElement object
     */
    public BuildElement writeOutput(OutputType outputType, File outputFile, ApkData apkData) {
    	File outputDir = outputFile.getParentFile();
    	BuildElement buildOutput = new BuildElement(
    			outputType,
                apkData,
                outputFile,
                Collections.emptyMap());
    	saveBuildElements(Collections.singletonList(buildOutput), outputDir);
        return buildOutput;
    }

    /**
     * Saves build element collection to given location
     * @param buildElements BuildElement collection
     * @param outputDir Location to write output file
     */
    public void saveBuildElements(Collection<BuildElement> buildElements, File outputDir) {
    	String serialized = elementRactory.persist(buildElements, outputDir.toPath());
    	File metadataFile = elementRactory.getMetadataFile(outputDir);
    	try (OutputStreamWriter writer = 
    			new OutputStreamWriter(
    					new FileOutputStream(metadataFile))) {
			writer.write(serialized);
		} catch (IOException e) {
			throw new AndworxException("Error writing ouput file " + metadataFile, e);
		}
    }
  
    /**
     * Computes all the project output and dependencies that must go into building the apk.
     * Excludes Android dependencies (AndmoreAndroidConstants.CONTAINER_DEPENDENCIES)
     * #param javaProject Java project resource
     * @throws CoreException
     */
    public List<File> gatherPaths(IJavaProject javaProject) throws CoreException {
    	List<File> pathList = new ArrayList<>();
        IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
        // Obtain the output of the main project (bin/classes)
        IPath path = javaProject.getOutputLocation();
        IResource outputResource = wsRoot.findMember(path);
        if (outputResource != null && outputResource.getType() == IResource.FOLDER) {
        	File classesFile = outputResource.getLocation().makeAbsolute().toFile();
        	pathList.add(classesFile);
        }

        // Scan classpath for exported entries, excluding Android dependencies
        IClasspathEntry[] classpaths = javaProject.readRawClasspath();
        if (classpaths != null) {
            for (IClasspathEntry entry : classpaths) {
                if (entry.isExported() && !entry.getPath().toString().equals(CONTAINER_DEPENDENCIES)) {
                    handleCPE(entry,javaProject,  pathList, wsRoot);
                }
            }
        }
        return pathList;
    }

	/**
	 * Creates Launch configuration for build processes such as desugar. Arguments and classpath are omitted by default.
	 * @param javaProcessInfo Information to run an external Java process
	 * @return ILaunchConfiguration object
	 */
	public ILaunchConfiguration createLaunchConfiguration(JavaProcessInfo javaProcessInfo, IFolder container) {
		String configName = javaProcessInfo.getDescription();
		// JVM arguments are converted to command line format
        StringBuilder jvmArgsString = new StringBuilder();
        Iterator<String> iterator = javaProcessInfo.getJvmArgs().iterator();
        if (iterator.hasNext()) {
        	jvmArgsString.append(iterator.next());
        	while (iterator.hasNext())
        		jvmArgsString.append(' ').append(iterator.next());
        }
		// Main class arguments are converted to command line format
        StringBuilder argsString = new StringBuilder();
        iterator = javaProcessInfo.getArgs().iterator();
        if (iterator.hasNext()) {
        	argsString.append(iterator.next());
        	while (iterator.hasNext())
        		argsString.append(' ').append(iterator.next());
        }
        // Classpath items are runtime paths
        List<org.eclipse.core.runtime.Path> classpath;
        if (javaProcessInfo.getClasspath().indexOf(File.pathSeparator) == -1)
        	classpath = Collections.singletonList(new org.eclipse.core.runtime.Path(javaProcessInfo.getClasspath()));
       	else {
       		classpath = new ArrayList<>();
       		String[] items = javaProcessInfo.getClasspath().split(File.pathSeparator);
       		for (String item: items)
       			classpath.add(new org.eclipse.core.runtime.Path(item));
       	}
        // Create and save configuration
        try {
	        ILaunchConfigurationWorkingCopy wc = getLaunchConfig(
	        		container,
    				configName, 
    				jvmArgsString.toString(), 
    				classpath, 
    				javaProcessInfo.getMainClass(), 
    				argsString.toString()); 
			// Save configuration in resource listener to avoid locked workspace
	        IWorkspace workspace = ResourcesPlugin.getWorkspace();
	        IResourceChangeListener listener = new IResourceChangeListener() {

				@Override
				public void resourceChanged(IResourceChangeEvent event) {
					if ((event.getType() == IResourceChangeEvent.POST_BUILD) ||
							(event.getType() == IResourceChangeEvent.PRE_CLOSE))
						try {
				        	saveLaunchConfig(wc, container);   
						} catch (CoreException e) {
						} finally { 
					        workspace.removeResourceChangeListener(this);
						}
				}};
	        workspace.addResourceChangeListener(listener, IResourceChangeEvent.POST_BUILD | IResourceChangeEvent.PRE_CLOSE);
        	return wc;
		} catch (CoreException e) {
	        String message = String.format("Error creating %s configuration", configName);
	        throw new AndworxException(message, e);
		}
	}

	/**
	 * Returns launch configuration identified by name
	 * @param configName Configuration name
	 * @return ILaunchConfiguration object or null if not found
	 */
	public ILaunchConfiguration getLaunchConfiguration(String configName) {
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type =
             manager.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        try {
			for (ILaunchConfiguration configuration: manager.getLaunchConfigurations(type)) 
			    if (configuration.getName().equals(configName)) 
			    	return configuration;
		} catch (CoreException e) {
			logger.warning("Launch manager error: %s", e.getMessage());
		}
		return null;
	}
	
	/**
     * Returns editable launch configuration for given parameters
     * @param configName Configuration name
     * @param vmArgs VM arguments
     * @param classpathList Classpath as Path list
     * @param main Main class
     * @param arguments Application arguments
     * @return ILaunchConfigurationWorkingCopy object
     * @throws CoreException
     */
    public ILaunchConfigurationWorkingCopy getLaunchConfig(
    		IContainer container,
    		String configName,  
    		String vmArgs, 
    		List<org.eclipse.core.runtime.Path> classpathList, 
    		String main, 
    		String arguments) throws CoreException {
        //andworxProject.getJavaProject().open(null);
        DebugPlugin debugPlugin = DebugPlugin.getDefault();
        ILaunchManager launchManager = debugPlugin.getLaunchManager();
        ILaunchConfigurationType launchConfigType = launchManager.getLaunchConfigurationType(
             IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        ILaunchConfigurationWorkingCopy wc = launchConfigType.newInstance(container, configName);
        // Set attribute to capture console output
        wc.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, true);
        //wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, andworxProject.getName());
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, vmArgs);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, main);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, arguments);
        List<String> classpath = new ArrayList<String>();
        for (org.eclipse.core.runtime.Path path: classpathList) {
        	classpath.add(getMomento(path));
        }
        classpath.add(getJreMemento());
        //classpath.add(getDefaulClasspathMemento());
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, classpath);
        //wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
        return wc;
    }

    /**
     * Returns XML classpath reference for given path
     * @param path The full path descriptor
     * @return momento
     * @throws CoreException
     */
	public String getMomento(Path path) throws CoreException {
		return JavaRuntime.newArchiveRuntimeClasspathEntry(path).getMemento();
	}
	
    /** 
     * Returns XML JRE container reference
     * @return momento
     * @throws CoreException
     */
	public String getJreMemento() throws CoreException {
		IRuntimeClasspathEntry container = JavaRuntime.newRuntimeContainerClasspathEntry(
			new Path( JavaRuntime.JRE_CONTAINER ), IRuntimeClasspathEntry.STANDARD_CLASSES, null /*javaProject.getJavaProject()*/);
		return container.getMemento();
	}

	/**
	 * Returns XML Default Project Classpath reference
	 * @return momento
	 * @throws CoreException
	 */
	public String getDefaulClasspathMemento() throws CoreException {
		DefaultProjectClasspathEntry defaultClasspath = new DefaultProjectClasspathEntry();
		return defaultClasspath.getMemento();
	}

	/**
	 * Save launch configuration
	 * @param wc Working copy of configuration
	 * @param container Location to store configuration
	 * @throws CoreException
	 */
	private void saveLaunchConfig(ILaunchConfigurationWorkingCopy wc, IFolder container) throws CoreException {
		if (!container.getParent().exists())
			((IFolder)container.getParent()).create(true, true, null);
		if (!container.exists())
			container.create(true, true, null);
		wc.doSave();
	}

	/**
	 * Handle classpath entry
	 * @param entry Classpath entry
	 * @param javaProject Java project resource
	 * @param pathList Classpath content
	 * @param wsRoot sWorkspace root
	 */
	private void handleCPE(IClasspathEntry entry, IJavaProject javaProject,
    		List<File> pathList, IWorkspaceRoot wsRoot) {

        // if this is a classpath variable reference, we resolve it.
        if (entry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
            entry = JavaCore.getResolvedClasspathEntry(entry);
        }

        if (entry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
            IProject refProject = wsRoot.getProject(entry.getPath().lastSegment());
            try {
                // ignore if it's an Android project, or if it's not a Java Project
                if (refProject.hasNature(JavaCore.NATURE_ID) &&
                        refProject.hasNature(NATURE_DEFAULT) == false) {
                    IJavaProject refJavaProject = JavaCore.create(refProject);

                    // get the output folder
                    IPath path = refJavaProject.getOutputLocation();
                    IResource outputResource = wsRoot.findMember(path);
                    if (outputResource != null && outputResource.getType() == IResource.FOLDER) {
                    	pathList.add(outputResource.getLocation().makeAbsolute().toFile());
                    }
                }
            } catch (CoreException exception) {
                // Not expected. Ignore.
            }

        } else if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
            handleClasspathLibrary(entry, pathList, wsRoot);
        } else if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
            // get the container
            try {
                IClasspathContainer container = JavaCore.getClasspathContainer(
                        entry.getPath(), javaProject);
                // ignore the system and default_system types as they represent
                // libraries that are part of the runtime.
                if (container != null && container.getKind() == IClasspathContainer.K_APPLICATION) {
                    IClasspathEntry[] entries = container.getClasspathEntries();
                    for (IClasspathEntry cpe : entries) {
                        handleCPE(cpe, javaProject, pathList, wsRoot);
                    }
                }
            } catch (JavaModelException jme) {
                // Not expected. Log only
                logger.error(jme, "Failed to resolve ClasspathContainer: %s", entry.getPath());
            }
        }
    }

	/**
	 * Handle library classpath entry
	 * @param entry Classpath entry
	 * @param pathList Classpath content
	 * @param wsRoot sWorkspace root
	 */
    private void handleClasspathLibrary(IClasspathEntry entry, List<File> pathList, IWorkspaceRoot wsRoot) {
        // ClasspathEntry IPath
        IPath path = entry.getPath();
        IResource resource = wsRoot.findMember(path);
        File fullPath = path.makeAbsolute().toFile();

        if (resource != null && resource.getType() == IResource.PROJECT) {
            // If it's a project we should just ignore it because it's going to be added
            // later when we add all the referenced projects.

        } else if (SdkConstants.EXT_JAR.equalsIgnoreCase(path.getFileExtension())) {
            // Case of a jar file (which could be relative to the workspace or a full path)
            if (resource != null && resource.exists() &&
                    resource.getType() == IResource.FILE) {
            	pathList.add(resource.getLocation().makeAbsolute().toFile());
            } else {
                // If the jar path doesn't match a workspace resource,
                // then we get an OSString and check if this links to a valid file.
                if (fullPath.isFile()) {
                	pathList.add(fullPath);
                } else {
                	logger.error(null, NO_LOCATE_ITEM, fullPath.toString());
                }
            }
        } else {
            // This can be the case for a class folder.
            if (resource != null && resource.exists() &&
                    resource.getType() == IResource.FOLDER) {
            	pathList.add(resource.getLocation().makeAbsolute().toFile());
            } else {
                // If the path doesn't match a workspace resource,
                // then we get an OSString and check if this links to a valid folder.
                if (fullPath.isDirectory()) {
                	pathList.add(fullPath);
                }
            }
        }
    }
}
