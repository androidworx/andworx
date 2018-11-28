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
package org.eclipse.andworx.registry;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.ProjectConfiguration;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

/**
 * Manages state of all Android projects
 */
public class ProjectRegistry {
	public static final String PROJECT_ERROR_MESSAGE = "Error creating project %s: %s";
	public static final String META_FILENAME_TEMPLATE = "%s-local";
	public static final String AAR_METAFILENAME = String.format(META_FILENAME_TEMPLATE, SdkConstants.EXT_AAR);
	
	private final AndworxContext objectFactory;
	/** Projects mapped to project states */
	private final Map<IProject, ProjectState> projectStateMap;
    /** Listeners for registration of new Projects */
    private final List<ProjectStateListener> projectStateListeners;
	/** Projects mapped to project profiles  */
	private Map<IProject, ProjectProfile> projectProfileMap;
	/** Listener for project resource changes */
	private volatile ProjectRegistrationListener projectListener;

	/**
	 * Construct ProjectRegistry object
	 */
	public ProjectRegistry() {
		projectProfileMap = new ConcurrentHashMap<>();
		projectStateMap = new ConcurrentHashMap<>();
		objectFactory = AndworxFactory.instance();
		projectStateListeners = new ArrayList<>();
	}

	/**
	 * Register project listener to capture custom event onProjectOpened()
	 * @param projectListener ProjectRegistrationListener object 
	 */
	public void registerProjectListener(ProjectRegistrationListener projectListener) {
		this.projectListener = projectListener;
	}

	/**
	 * Returns flag set true if given project has benn assigned a ProjectState.
	 * Use this test before accessing a project which may be in the process of being opened
	 * @param project
	 * @return boolean
	 */
	public boolean hasProjectState(IProject project) {
		return projectStateMap.containsKey(project);
	}
	
	/**
	 * Returns copy of the list of all project states
	 * @return ImmutableList object
	 */
	public ImmutableList<ProjectState> projectStateCollection() {
		return ImmutableList.copyOf(projectStateMap.values());
	}

	public void addProjectListener(ProjectStateListener listener) {
		projectStateListeners.add(listener);
	}
	
	/**
	 * Returns profile for specified project. The profile is created if not already cached
	 * and this is expected to occur while initailizing the project classpath.
	 * @param project
	 * @return ProjectProfile object
	 */
	public ProjectProfile getProjectProfile(IProject project) {
		ProjectProfile profile = projectProfileMap.get(project);
		if (profile == null) {
			// Profile constructor requires project location on file system and AndroidEnvironment object
			// Other objects are fetched using JPA from the configuration database
			File projectLocation = project.getLocation().makeAbsolute().toFile();
	    	profile = objectFactory.getProjectProfile(project.getName(), projectLocation);
		}
		return profile;
	}

	/**
	 * Associates project profile to project
	 * @param project
	 * @param profile ProjectProfile object
	 */
	public void putProjectProfile(IProject project, ProjectProfile profile) {
		projectProfileMap.put(project, profile);
	}

    /**
     * Returns project state for given project, creating it if it does not already exist.
     * Called from AndroidClasspathContainerInitializer.initialize()
     * @param javaProject
     * @return ProjectState object
     */
    public ProjectState setProjectState(IJavaProject javaProject) {
		ProjectState state = projectStateMap.get(javaProject.getProject());
		if (state == null) {
			ProjectProfile profile = projectProfileMap.get(javaProject.getProject());
			if (profile != null) {
				return setProjectState(javaProject, profile);
			}
			String projectName = javaProject.getElementName();
			File projectLocation = javaProject.getProject().getLocation().makeAbsolute().toFile();
			ProjectConfiguration projectConfig = objectFactory.getProjectConfig(projectName, projectLocation);
			projectProfileMap.put(javaProject.getProject(), projectConfig.getProfile());
			state = setProjectState(javaProject, projectConfig);
			for (ProjectStateListener listener: projectStateListeners)
				listener.onProjectOpened(state);
		}
		return state;
    }
 
    /**
     * Creates and returns project state for given project
     * @param javaProject
     * @param profile Project profile containing important project attributes and project entity ID
     * @return ProjectState object
      */
	public ProjectState setProjectState(IJavaProject javaProject, ProjectProfile profile)  {
		ProjectState state = projectStateMap.get(javaProject.getProject());
		if (state == null) {
			String projectName = javaProject.getElementName();
			File projectLocation = javaProject.getProject().getLocation().makeAbsolute().toFile();
			AndworxContext objectFactory = AndworxFactory.instance();
			ProjectConfiguration projectConfig = objectFactory.getProjectConfig(profile, projectName, projectLocation);
			projectProfileMap.put(javaProject.getProject(), projectConfig.getProfile());
			state = setProjectState(javaProject, projectConfig);
			for (ProjectStateListener listener: projectStateListeners)
				listener.onProjectOpened(state);
		}
		return state;
	}
	
    /**
     * Returns the {@link ProjectState} object associated with a given project.
     * @param project
     * @return ProjectState object
     */
	public ProjectState getProjectState(IProject project) {
        ProjectState state = projectStateMap.get(project);
        if (state == null) // This is not expected
        	throw new AndworxException("Project " + project.getName() + " ecountered with no state");
		return state;
	}

	/**
	 * Remove project from cache
	 * @param removedProject
	 */
	public void remove(IProject removedProject) {
		ProjectState projectState = projectStateMap.remove(removedProject);
		projectListener.onProjectRemoved(projectState, projectState.getLibraries());
	}

	/**
	 * Force all projects to reload targets following an Android SDk change
	 */
	public void refresh() {
        synchronized (this) {
            for (ProjectState state: projectStateMap.values()) {
                state.setTargetSet(false);;
            }
        }
	}

	/**
	 * Returns a list of all libtary states associated with given project
	 * @param project
	 * @return LibraryState list
	 */
	public List<LibraryState> findLibraries(IProject project) {
		List<LibraryState> libraryList = new ArrayList<>();
        for (ProjectState item : projectStateMap.values()) {
            LibraryState libState = item.getLibrary(project);
            if (libState != null) 
            	libraryList.add(libState);
        }
        return libraryList;
	}

    /**
     * Returns a list of {@link ProjectState} representing projects depending, directly or
     * indirectly on a given library project.
     * @param project the library project.
     * @return a possibly empty list of ProjectState.
     */
    @NonNull
    public Set<ProjectState> getMainProjectsFor(IProject project) {
        synchronized (this) {
            // first get the project directly depending on this.
            Set<ProjectState> list = new HashSet<ProjectState>();

            // loop on all project and see if ProjectState.getLibrary returns a non null
            // project.
            for (Entry<IProject, ProjectState> entry : projectStateMap.entrySet()) {
                if (project != entry.getKey()) {
                    LibraryState library = entry.getValue().getLibrary(project);
                    if (library != null) {
                        list.add(entry.getValue());
                    }
                }
            }

            // now look for projects depending on the projects directly depending on the library.
            HashSet<ProjectState> result = new HashSet<ProjectState>(list);
            for (ProjectState p : list) {
                if (p.isLibrary()) {
                    Set<ProjectState> set = getMainProjectsFor(p.getProject());
                    result.addAll(set);
                }
            }
            return result;
        }
    }

    /**
     * Returns target configured for given project
     * @param project Eclipse project resource
     * @return IAndroidTarget object
     */
	public IAndroidTarget getTarget(IProject project) {
		ProjectProfile profile = getProjectProfile(project);
		if (profile == null) 
			throw new IllegalStateException("No profile found for project " + project.getName());
		return profile.getTarget();
	}
	
	/**
	 * Update project states on Android SDK platform loaded
	 * @param target Android target
	 * @param projectsToResolve List of java projects that are pending target configuration
	 * @return list of projects to be updated
	 */
	public Set<IJavaProject> onTargetLoaded(IAndroidTarget target, Set<IJavaProject> projectsToResolve ) {
		Set<IJavaProject> targetReadyProjects = Sets.newHashSet();
        for (IJavaProject javaProject : projectsToResolve) {
        	//logger.info("Checking project: " + javaProject.getElementName());
            ProjectState projectState = getProjectState(javaProject.getProject());
            String hashString = projectState.getProfile().getTargetHash();
            boolean isProjectTarget = hashString.equals(AndroidTargetHash.getTargetHashString(target));
            if (isProjectTarget && javaProject.getProject().isOpen()) {
            	//logger.info("Target is " + hashString);
                // Projects resolved before the sdk is loaded
                // will have a ProjectState with null IAndroidTarget,
                // so we load the target now that the target is loaded.
             	loadTargetAndBuildTools(projectState);
            	targetReadyProjects.add(javaProject);
             }
        }
        return targetReadyProjects;
	}

	public IAndroidTarget loadTargetAndBuildTools(IProject project) {
 		return loadTargetAndBuildTools(projectStateMap.get(project));
	}
	
	public IAndroidTarget loadTargetAndBuildTools(ProjectState projectState) {
	    IAndroidTarget target = null;
 		if (projectState != null) {
        	target = projectState.loadTarget();
        	if (target != null) {
        	    String status = "";
        		BuildToolInfo buildToolsInfo = projectState.setBuildToolsInfo();
                if (buildToolsInfo == null) {
                	status = String.format("Unable to resolve %s property value '%s'",
                                       ProjectProperties.PROPERTY_BUILD_TOOLS,
                                        projectState.getBuildToolInfoVersion());
                }
                projectListener.onBuildToolsStatus(projectState, status);
        	}
     	}
       return target;
	}

    /**
     * Creates and returns project state for given project
     * @param javaProject
     * @param projectConfig Project configuration
     * @return ProjectState object
     */
	private ProjectState setProjectState(IJavaProject javaProject, ProjectConfiguration projectConfig) {
		int projectId = projectConfig.getProfile().getProjectId();
		if (projectId == AndroidConfiguration.VOID_PROJECT_ID)
			throw new AndworxException("Project " + javaProject.getElementName() + " does not have a configuration entry");
		AndworxProject andworxProject = new AndworxProject(projectConfig, objectFactory.getSdkTracker().getSdkProfile());
    	ProjectState state = new ProjectState(javaProject, projectConfig.getProfile(), andworxProject);
    	put(javaProject.getProject(), state);
    	if (projectListener != null) { // projectListener should never be null
    		state.scanLibraries(projectStateMap.values(), projectListener);
    		projectListener.onProjectOpened(state);
    	}
    	return state;
	}
	
	private void put(IProject project, ProjectState state) {
		projectStateMap.put(project, state);
		state.setTargetSet(state.getProfile().getTarget() != null);
	}

	
}
