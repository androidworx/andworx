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
package org.eclipse.andworx.helper;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.repo.ProjectRepository;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;

/**
 * Project creation and build helper
 */
public class ProjectBuilder {
    public static final String LAUNCH_PATH = "launch";
    public static final String DESUGAR = "Desugar";
    public static final String D8 = "D8";

    public static SdkLogger logger = SdkLogger.getLogger(ProjectBuilder.class.getName());

    /** Utility to facilitate project builds */
    private final BuildHelper buildHelper;
    /** Java project resource */
    private final IJavaProject javaProject;
    /** Project profile */
    @NonNull 
	private final ProjectProfile profile;
    /** System property "sun.boot.class.path" */
    @NonNull 
    private final List<Path> compilationBootclasspath;
    /** Location of project launch configurations */
    @NonNull 
	private final File launchContainerRoot;

    /**
     * Construct ProjectBuilder object dedicated to specified project
     * @param profile Project profile with identity and configured information
     * @param context Variant context
     * @param javaProject Java project resource
     */
	public ProjectBuilder(ProjectProfile profile, IJavaProject javaProject, BuildHelper buildHelper) {
		this.profile = profile;
		this.javaProject = javaProject;
		this.buildHelper = buildHelper;
        compilationBootclasspath = PathUtils.getClassPathItems(System.getProperty("sun.boot.class.path"));
		File projectLocation = javaProject.getProject().getLocation().makeAbsolute().toFile();
		launchContainerRoot = new File(projectLocation, LAUNCH_PATH);
	}

	/**
	 * Returns Location of project launch configurations  
	 * @return File object
	 */
	public File getLaunchContainerRoot() {
		return launchContainerRoot;
	}

    /**
     * Returns Android dependency jars. Theres are libary jars or files extracted from AARs
     * @return File list
     */
    public List<File> getAndroidDependencyJars() {
		List<File> jarFiles = new ArrayList<>();
		// Android dependencies are specified in the project profile
        Set<Dependency> dependencies = profile.getDependencies();
        if (!dependencies.isEmpty()) {
        	// The ImportEnvironment contains information to access the expanded AAR repository
        	AndworxContext objectFactory = AndworxFactory.instance();
    	    File repositoryLocation = objectFactory.getAndroidEnvironment().getRepositoryLocation();
            try {
				ProjectRepository projectRepository = new ProjectRepository(repositoryLocation);
	            for (Dependency dependency: profile.getDependencies()) {
	            	if (dependency.isLibrary()) { 
	            		// Fetch jar from expanded AAR if it exists. If an AAR classes.jar does not contain any classes
	            		// then it is excluded from the expansion.
	            		Identity identity = dependency.getIdentity();
	                	File projectDirectory = projectRepository.getMetadataPath(identity, SdkConstants.EXT_AAR).getParentFile();
	                	File libs = new File(projectDirectory, "libs");
	                	if (libs.exists() && (libs.listFiles().length > 0)) {
		                	File jarFile = FileUtils.join(projectDirectory, "libs", identity.getArtifactId() + SdkConstants.DOT_JAR);
		                	jarFiles.add(jarFile); 
	                	}
	            	} else if (dependency.getPath() != null)
	             		jarFiles.add(dependency.getPath());
	            }
			} catch (NoLocalRepositoryManagerException e) {
                logger.error(e, "Failed to open local repository at " + repositoryLocation.toString());
			}
        }
        return jarFiles;
	}

    /**
     * Returns list of files to include in export classpath for given project
     * @param javaProject Java project resource
     * @return File list
     */
    public List<File> getExportClasspath(IJavaProject javaProject) {
    	List<File> pathList = new ArrayList<>();
    	try {
			pathList.addAll(buildHelper.gatherPaths(javaProject));
		} catch (CoreException e) {
			throw new AndworxException("Error gathering export classpath", e);
		}
    	pathList.addAll(getAndroidDependencyJars());
    	return pathList;
    }
    
    /**
     * Returns compilation boot classpath (obtained from System property "sun.boot.class.path")
     * @return Path list
     */
	public List<Path> getCompilationBootclasspath() {
		return compilationBootclasspath;
	}

	/**
	 * Returns launch configuration for specified name
	 * @param configName Configuration name
	 * @param javaProcessInfo Information to run an external java process
	 * @return ILaunchConfiguration object or null if the configuration does not exist
	 */
	public ILaunchConfiguration getLaunchConfiguration(String configName, JavaProcessInfo javaProcessInfo) {
       	// Find template
		ILaunchConfiguration templateLaunchConfig = findLaunchTemplate(configName);
        try {
			if (templateLaunchConfig == null) {
				IFolder launchFolder = javaProject.getProject().getFolder(LAUNCH_PATH);
				IFolder container = launchFolder.getFolder(configName);
				templateLaunchConfig = 
						buildHelper.createLaunchConfiguration(javaProcessInfo, container);
			}
			String projectName = javaProject.getElementName();
			configName = projectName + "." + configName;
			ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
			for (ILaunchConfiguration projectConfig: 
				manager.getLaunchConfigurations(manager
					   .getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION))) 
			    if (projectConfig.getName().equals(configName)) 
			    	return projectConfig;
		    ILaunchConfigurationWorkingCopy wc = templateLaunchConfig.copy(configName);
	        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, projectName);
	        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
	        return wc;
		} catch (CoreException e) {
			logger.warning("Launch manager error: %s", e.getMessage());
		}
        return null;
	}

	
	/**
     * Returns launch cofiguration identified by name
     * @param configName name of configuration
     * @return ILaunchConfiguration object or null if not found
     */
	private ILaunchConfiguration findLaunchTemplate(String configName) {
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type =
             manager.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        try {
			for (ILaunchConfiguration configuration: manager.getLaunchConfigurations(type)) 
			    if (configuration.getName().equals(configName)) 
			    	return configuration;
		} catch (CoreException e) {
			//logger.warning("Launch manager error: %s", e.getMessage());
		}
		return null;
	}

}
