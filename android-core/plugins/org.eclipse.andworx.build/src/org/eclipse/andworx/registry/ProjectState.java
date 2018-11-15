/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static com.android.builder.core.BuilderConstants.DEBUG;
//import static com.android.builder.core.BuilderConstants.RELEASE;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.AndroidSourceSet;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.model.ProjectSourceProvider;
import org.eclipse.andworx.model.SourcePath;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.xml.sax.SAXException;

import com.android.annotations.Nullable;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.io.FileWrapper;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.ImmutableList;

/**
 * Centralized state for Android Eclipse project.
 * <p>Contains the project profile, Android context and variants.
 *   
 * Also contains library information (incubating).
 * {@link #isLibrary()} indicates if the project is a library.
 * {@link #hasLibraries()} and {@link #getLibraries()} give access to the libraries through
 * instances of {@link LibraryState}. A {@link LibraryState} instance is a link between a main
 * project and its library. Theses instances are owned by the {@link ProjectState}.
 *
 * {@link #isMissingLibraries()} will indicate if the project has libraries that are not resolved.
 * Unresolved libraries are libraries that do not have any matching opened Eclipse project.
 * When there are missing libraries, the {@link LibraryState} instance for them will return null
 * for {@link LibraryState#getProjectState()}.
 *
 */
public final class ProjectState {
	/** Library update status */
    public static class LibraryDifference {
        public boolean removed = false;
        public boolean added = false;

        public boolean hasDiff() {
            return removed || added;
        }
    }

    /** Eclipse project resource */
    private final IProject project;
    /** Project characteristics including identity, dependencies and Android target platform */
    private final ProjectProfile profile;
    /** Project-specific Android configuration */
    private final AndworxProject andworxProject;
    private final VariantContext context;
    /** Helper to assist with Android build tasks */
    private final ProjectBuilder projectBuilder;
    private final AndroidBuilder androidBuilder;
	private final AndworxFactory objectFactory;
    /** Information required to locate build tools */
    private BuildToolInfo buildToolInfo;
    /** Status result of last attempt to open the project or null if not yet opened */
    private IStatus openStatus;
    /** Flag set false if target platform needs to be refreshed */
	private boolean isTargetSet;
	/** Flag set true if new libraries encountered on opening project */
	boolean foundLibraries;

    /**
     * list of libraries. Access to this list must be protected by
     * <code>synchronized(libraries)</code>
     */
    private final ArrayList<LibraryState> libraries;
    /** Cached list of all IProject instances representing the resolved libraries, including
     * indirect dependencies. This must never be null. */
    private List<ProjectState> libraryProjects;
    /**
     * List of parent projects. When this instance is a library ({@link #isLibrary()} returns
     * <code>true</code>) then this is filled with projects that depends on this project.
     */
    private final ArrayList<ProjectState> parentProjects;

    /** List of pending library projects to be loaded, each with a unique identity */  
    private List<Identity> pendingProjects;

    /**
     * Construct ProjectState object
     * @param javaProject Eclipse Java project resource
     * @param profile Project characteristics
     * @param andworxProject Project-specific Android configuration
     */
    ProjectState(IJavaProject javaProject, ProjectProfile profile, AndworxProject andworxProject) {
        if (javaProject == null || profile == null) {
            throw new NullPointerException();
        }
        this.project = javaProject.getProject();
        this.profile = profile;
        this.andworxProject = andworxProject;
        libraries = new ArrayList<LibraryState>();
       	libraryProjects = Collections.emptyList();
       	parentProjects = new ArrayList<ProjectState>();
       	pendingProjects = new ArrayList<>();
       	context = andworxProject.getContexts().get(DEBUG);
       	objectFactory = AndworxFactory.instance();
       	androidBuilder = objectFactory.getAndroidBuilder(context);
       	projectBuilder = objectFactory.getProjectBuilder(javaProject, profile);
    }

    /**
     * Returns Eclipse project resource
     * @return IProject object
     */
	public IProject getProject() {
        return project;
    }

	/**
	 * Returns Project-specific Android configuration
	 * @return AndworxProject object
	 */
    public AndworxProject getAndworxProject() {
		return andworxProject;
	}

    /**
     * Returns project characteristics 
     * @return ProjectProfile object
     */
	public ProjectProfile getProfile() {
		return profile;
	}

	/**
	 * Set information required to locate build tools
	 * @param buildToolInfo BuildToolInfo object
	 */
    public void setBuildToolInfo(BuildToolInfo buildToolInfo) {
        this.buildToolInfo = buildToolInfo;
    }

    /**
     * Returns information required to locate build tools
     * @return BuildToolInfo object
     */
    public BuildToolInfo getBuildToolInfo() {
        return buildToolInfo;
    }

    /**
     * Returns Android variant context
     * @return VariantContext object
     */
    public VariantContext getContext() {
		return context;
	}

    /**
     * Returns helper to assist with Android build tasks
     * @return ProjectBuilder object
     */
    public ProjectBuilder getProjectBuilder() {
    	return projectBuilder;
    }
    
	/**
     * Returns the build tools version from the project's properties.
     * @return the value or null
     */
    @Nullable
    public String getBuildToolInfoVersion() {
        return profile.getBuildToolsVersion();
    }

    /**
     * Returns flag set true if Renderscript support mode enabled
     * @return boolean
     */
    public boolean getRenderScriptSupportMode() {
        return getContext().getVariantConfiguration().getRenderscriptSupportModeEnabled();
    }

    /**
     * Returns result of last attempt to open the project
     * @return IStatus object or null if the project has not been opened
     */
    public IStatus getOpenStatus() {
		return openStatus;
	}

    /**
     * Set result of last attempt to open the project
     * @param openStatus IStatus object
     */
	public void setOpenStatus(IStatus openStatus) {
		this.openStatus = openStatus;
	}

    public boolean isFoundLibraries() {
		return foundLibraries;
	}

	/**
     * Returns list of parent project states
     * @return list of {@link LProjectState}
     */
    public List<ProjectState> getParentProjects() {
        return Collections.unmodifiableList(parentProjects);
    }

    /**
     * Computes the transitive closure of projects referencing this project as a
     * library project
     *
     * @return a collection (in any order) of project states for projects that
     *         directly or indirectly include this project state's project as a
     *         library project
     */
    public Collection<ProjectState> getFullParentProjects() {
        Set<ProjectState> result = new HashSet<ProjectState>();
        addParentProjects(result, this);
        return result;
    }

	/**
	 * Returns flag set true if an available target has been assigned
	 * @return boolean
	 */
    public boolean isTargetSet() {
		return isTargetSet;
	}

    /**
     * Set flag for available target has been assigned
     * @param isTargetSet
     */
	public void setTargetSet(boolean isTargetSet) {
		this.isTargetSet = isTargetSet;
	}

	public IAndroidTarget loadTarget() {
	    IAndroidTarget target = profile.getTarget();
 		if ((target != null) && !isTargetSet()) {
        	setTargetSet(true);
		}
        return target;
	}

    public BuildToolInfo setBuildToolsInfo() {
    	BuildToolInfo buildToolsInfo = null;
        String buildToolInfoVersion = getBuildToolInfoVersion();
        if (buildToolInfoVersion != null) {
            buildToolsInfo = objectFactory.getBuildToolInfo(buildToolInfoVersion);
            if (buildToolsInfo != null) {
                setBuildToolInfo(buildToolsInfo);
            }
        }
        return buildToolsInfo;
	}
    
    /**
     * Returns path of source folder relative to the project locations
     * @param codeSource CodeSource enum to identifiy source type
     * @return relative path
     */
	public String getProjectSourceFolder(CodeSource codeSource) {
		ProjectSourceProvider sourceSet = andworxProject.getProjectSourceProvider();
		List<SourcePath> sourcePaths = sourceSet.getSourcePathList(codeSource);
		if (sourcePaths.isEmpty()) { // This is not expected
			return AndroidSourceSet.DEFAULT_PROJECT_ROOT + "/" + codeSource.defaultPath;
		}
		return sourcePaths.get(0).getPath();
	}

	/**
	 * Returns main application ID 
	 * @return application ID
	 */
	public String getOriginalApplicationId() {
		return getContext().getVariantConfiguration().getOriginalApplicationId();
	}

	/**
	 * Returns processes configured in main manifest
	 * @return array of process identities
	 */
	public String[] getProcessesFromManifest() {
		File manifestFile = getContext().getAndworxProject().getDefaultConfig().getSourceProvider().getManifestFile();
		if (manifestFile.exists()) {
           ManifestData data = parse(manifestFile);
           if (data != null){
               return data.getProcesses();
           }
		}
        return new String[] {};
	}

    /**
     * Returns list of libraries
     * @return list of {@link LibraryState}.
     */
    public List<LibraryState> getLibraries() {
        return ImmutableList.copyOf(libraries);
    }

    /**
     * Returns list of pending library project identitiess to be loaded 
     * @return list of {@link Identity}.
     */
    public List<Identity> getPendingLibraries() {
        synchronized (pendingProjects) {
            return Collections.unmodifiableList(pendingProjects);
        }
    }

    /**
     * Returns all the <strong>resolved</strong> library projects, including indirect dependencies.
     * The list is ordered to match the library priority order for resource processing with
     * <code>aapt</code>.
     * <p/>If some dependencies are not resolved (or their projects is not opened in Eclipse),
     * they will not show up in this list.
     * @return list of resolved projects - may be an empty.
     */
    public List<ProjectState> getResolvedLibraryProjects() {
        return ImmutableList.copyOf(libraryProjects);
    }
    
    /**
     * Returns all the <strong>resolved</strong> library projects, including indirect dependencies.
     * The list is ordered to match the library priority order for resource processing with
     * <code>aapt</code>.
     * <p/>If some dependencies are not resolved (or their projects is not opened in Eclipse),
     * they will not show up in this list.
     * @return the resolved projects as an unmodifiable list. May be an empty.
     */
    public List<IProject> getFullLibraryProjects() {
    	synchronized (libraryProjects) {
    		final List<IProject> projectList = new ArrayList<>(libraryProjects.size());
    		libraryProjects.forEach(library -> projectList.add(library.getProject()));
    		return projectList;
    	}
    }
    
    /**
     * Returns flag set true if this is a library project.
     * @return boolean
     */
    public boolean isLibrary() {
        return profile.isLibrary(); 
    }

    /**
     * Returns flag set true if the project depends on one or more libraries.
     * @return boolean
     */
    public boolean hasLibraries() {
        synchronized (libraries) {
            return libraries.size() > 0;
        }
    }

    /**
     * Returns flage set true if the project is missing at least one required library
     * @return boolean
     */
    public boolean isMissingLibraries() {
        synchronized (libraries) {
            for (LibraryState state : libraries) {
                if (state.getProjectState() == null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the {@link LibraryState} object for a given {@link IProject}.
     * </p>This can only return a non-null object if the link between the main project's
     * {@link IProject} and the library's {@link IProject} was done.
     *
     * @return the matching LibraryState or <code>null</code>
     *
     * @see #needs(ProjectState)
     */
    public LibraryState getLibrary(IProject library) {
        synchronized (libraries) {
            for (LibraryState state : libraries) {
                ProjectState ps = state.getProjectState();
                if (ps != null && ps.getProject().equals(library)) {
                    return state;
                }
            }
        }
        return null;
    }

    /**
     * Returns the {@link LibraryState} object for a given <var>name</var>.
     * </p>This can only return a non-null object if the link between the main project's
     * {@link IProject} and the library's {@link IProject} was done.
     *
     * @return the matching LibraryState or <code>null</code>
     *
     * @see #needs(IProject)
     */
    public LibraryState getLibrary(String name) {
        synchronized (libraries) {
            for (LibraryState state : libraries) {
                ProjectState ps = state.getProjectState();
                if (ps != null && ps.getProject().getName().equals(name)) {
                    return state;
                }
            }
        }
        return null;
    }


    /**
     * Returns whether a given library project is needed by the receiver.
     * <p/>If the library is needed, this finds the matching {@link LibraryState}, initializes it
     * so that it contains the library's {@link IProject} object (so that
     * {@link LibraryState#getProjectState()} does not return null) and then returns it.
     *
     * @param libraryProject the library project to check.
     * @return a non null object if the project is a library dependency,
     * <code>null</code> otherwise.
     *
     * @see LibraryState#getProjectState()
     */
    public LibraryState needs(ProjectState libraryProject) {
    	Identity libIdentity = libraryProject.getProfile().getIdentity();
        // Loop on all libraries and check for dependency match
        synchronized (libraries) {
            for (LibraryState libraryState : libraries) {
                if (libraryState.getProjectState() == null) {
                	// This dependency is unresolved
                     if (libIdentity.equals(libraryState.getIdentity())) {
                    	libraryState.setProject(libraryProject);
                        return libraryState;
                    }
                }
            }
        }
        return null;
    }

	/**
     * Returns whether the project depends on a given <var>library</var>
     * @param library the library to check.
     * @return true if the project depends on the library. This is not affected by whether the link
     * was done through {@link #needs(ProjectState)}.
     */
    public boolean dependsOn(ProjectState library) {
        synchronized (libraries) {
            for (LibraryState state : libraries) {
                if (state != null && state.getProjectState() != null &&
                        library.getProject().equals(state.getProjectState().getProject())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Update the full library list, including indirect dependencies. The result is returned by
     * {@link #getFullLibraryProjects()}.
     */
    public void updateFullLibraryList() {
        ArrayList<ProjectState> list = new ArrayList<>();
        synchronized (libraries) {
            buildFullLibraryDependencies(libraries, list);
        }

        libraryProjects = Collections.unmodifiableList(list);
    }

    /**
     * Update pending library lists
     * @param state Newly added project state
     */
	public void resolve(ProjectState state) {
        // compute current location
		if (state.getProject() == null)
			return;
        Identity resolved = null;
        Identity identity = state.getProfile().getIdentity();
        // loop on all libraries and check if the path match
        synchronized (pendingProjects) {
			for (Identity projectIdentity: pendingProjects) {
	            if (projectIdentity.equals(identity)) {
	            	resolved = projectIdentity;
	            	break;
	            }
			}
        }
        if (resolved != null) {
            synchronized (pendingProjects) {
       	        pendingProjects.remove(resolved);
            }
        	if (pendingProjects.isEmpty())
        		updateFullLibraryList();
        }
	}

    public void scanLibraries(Collection<ProjectState> allProjects, ProjectRegistrationListener projectStateListener) {
        final boolean isLibrary = isLibrary();
        final boolean hasLibraries = hasLibraries();
        if (isLibrary || hasLibraries) {
            foundLibraries = false;
            // loop on all the existing project and update them based on this new
            // project
            for (ProjectState projectState : allProjects) {
                if (projectState != this) {
                    // If the project has libraries, check if this project
                    // is a reference.
                    if (hasLibraries) {
                        // ProjectState#needs() both checks if this is a missing library
                        // and updates LibraryState to contains the new values.
                        // This must always be called.
                        LibraryState libState = needs(projectState);

                        if (libState != null) {
                            // found a library! Add the main project to the list of
                            // modified project
                        	projectStateListener.onDependencyResolved(projectState);
                        }
                    }

                    // if the project is a library check if the other project depend
                    // on it.
                    if (isLibrary) {
                        // ProjectState#needs() both checks if this is a missing library
                        // and updates LibraryState to contains the new values.
                        // This must always be called.
                        LibraryState libState = projectState.needs(this);
                        if (libState != null)
                        	projectStateListener.onLibraryFound(this);
                    }
                }
            }
        }
    }

    /**
     * Returns the Android boot classpath to be used during compilation.
     * @param includeOptionalLibraries if true, optional libraries are included even if not
     *                                 required by the project setup.
     */
	public List<File> getBootClasspath(boolean includeOptionalLibraries) {
		return androidBuilder.getBootClasspath(includeOptionalLibraries);
	}

    /**
     * Returns mesage receiver
     * @return MessageReceiver object
     */
    public MessageReceiver getMessageReceiver() {
    	return androidBuilder.getMessageReceiver();
    }

    /**
     * Returns the "created by" tag for the packaged manifest.
     *
     * @return the "created by" tag or {@code null} if no tag was defined
     */
    public String createdBy() {
    	return androidBuilder.getCreatedBy();
    }
    
    /**
     * Returns Android dependency jars. Theres are libary jars or files extracted from AARs
     * @return File list
     */
	public List<File> getAndroidDependencyJars() {
		return projectBuilder.getAndroidDependencyJars();
	}
	
	/**
	 * Add parent project state
	 * @param parentState ProjectState obejct to add
	 */
	void addParentProject(ProjectState parentState) {
        parentProjects.add(parentState);
    }

	/**
	 * Remove parent project state
	 * @param parentState ProjectState obejct to remove
	 */
    void removeParentProject(ProjectState parentState) {
        parentProjects.remove(parentState);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ProjectState) {
            return project.equals(((ProjectState) obj).project);
        }
        
        if (obj instanceof IProject) {
            return project.equals(obj);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return project.hashCode();
    }

    @Override
    public String toString() {
        return project.getName();
    }

    /** 
     * Use recursion to add all parent projects of the given project, transitively, into the given parent set 
     */
    private static void addParentProjects(Set<ProjectState> parents, ProjectState state) {
        for (ProjectState s : state.parentProjects) {
            if (!parents.contains(s)) {
                parents.add(s);
                addParentProjects(parents, s);
            }
        }
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a full list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     * @param inLibraries the libraries to resolve
     * @param outLibraries where to store all the libraries.
     */
    private void buildFullLibraryDependencies(List<LibraryState> inLibraries,
            ArrayList<ProjectState> outLibraries) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        for (int i = inLibraries.size() - 1  ; i >= 0 ; i--) {
            LibraryState library = inLibraries.get(i);

            // Get its libraries if possible
            ProjectState libProjectState = library.getProjectState();
            if (libProjectState != null) {
                List<LibraryState> dependencies = libProjectState.getLibraries();

                // Build the dependencies for those libraries
                buildFullLibraryDependencies(dependencies, outLibraries);

                // and add the current library (if needed) in front (higher priority)
                if (outLibraries.contains(libProjectState) == false) {
                    outLibraries.add(0, libProjectState);
                }
            } else {
                // Match project idententy to library dependency
               	synchronized (pendingProjects) {
                    if (library.getIdentity().equals(profile.getIdentity()))
                         pendingProjects.add(library.getIdentity());
               	}
            }
        }
    }

    
    /**
     * Parses the Android Manifest, and returns an object containing the result of the parsing.
     * @param manifestFile the {@link IFile} representing the manifest file.
     * @return an {@link ManifestData}
     * @throws AndworxException
     */
    public ManifestData parse(File manifestFile) { 
        try { 
			return AndroidManifestParser.parse(new FileWrapper(manifestFile), true, null);
		} catch (IOException | SAXException e) { // Not expected after project successfully opened
			throw new AndworxException(manifestFile.toString(), e);
		}
    }

}
