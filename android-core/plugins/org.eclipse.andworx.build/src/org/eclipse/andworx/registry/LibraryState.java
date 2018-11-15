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

import org.eclipse.core.resources.IProject;

import java.util.Objects;

import org.eclipse.andworx.project.Identity;

/**
 * A class that represents a library linked to a project.
 * <p/>It does not represent the library uniquely. Instead the {@link LibraryState} is linked
 * to the parent project which is accessible through {@link #getParentProjectState()}.
 * <p/>If a library is used by two different projects, then there will be two different
 * instances of {@link LibraryState} for the library.
 *
 * @see ProjectState#getLibrary(IProject)
 */
public class LibraryState {
	/* Owner project state */
	private final ProjectState parentProjectState;
	/** Library project identity */
    private Identity identity;
    /** Library project state */
    private ProjectState projectState;

    /**
     * Construct LibraryState object 
     * @param parentProjectState Oner project state
     * @param identity Unique library project identity
     */
    LibraryState(ProjectState parentProjectState, Identity identity) {
    	this.parentProjectState = parentProjectState;
        this.identity = identity;
    }

    /**
     * Returns the {@link ProjectState} of the parent project using this library.
     */
    public ProjectState getParentProjectState() {
        return parentProjectState;
    }

    /**
     * Closes the library. This resets the IProject from this object ({@link #getProjectState()} will
     * return <code>null</code>), and updates the parent project data so that the library
     * {@link IProject} object does not show up in the return value of
     * {@link ProjectState#getFullLibraryProjects()}.
     */
    public void close() {
        projectState.removeParentProject(getParentProjectState());
        projectState = null;
        getParentProjectState().updateFullLibraryList();
    }

    /**
     * Set library project identity
     * @param identity Identity object
     */
    public void setIdentity(Identity identity) {
        this.identity = identity;
    }

    /**
     * Set resolved library project state
     * @param projectState Project state
     */
    public void setProject(ProjectState projectState) {
        this.projectState = projectState;
        // Link library to owner
        this.projectState.addParentProject(getParentProjectState());
        // Update the full resolved library list
        getParentProjectState().updateFullLibraryList();
        // Resolve all parents sharing this library
        for (ProjectState parentState : projectState.getParentProjects()) {
        	parentState.resolve(projectState);
        }
    }

    /**
     * Returns the library identity specification from the parent project.
     * <p/>This is identical to the value defined in the parent project's project.properties.
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Returns the {@link ProjectState} item for the library. This can be null if the project
     * is not actually opened in Eclipse.
     */
    public ProjectState getProjectState() {
        return projectState;
    }

    @Override
    public boolean equals(Object obj) {
    	// Match LibraryStates by having same identity and parent projects
        if (obj instanceof LibraryState) {
            LibraryState objState = (LibraryState)obj;
            return identity.equals(objState.identity) &&
                    getParentProjectState().equals(objState.getParentProjectState());
        }
    	// Match a LibraryState to a ProjectState by ProjectState
        if (obj instanceof ProjectState || obj instanceof IProject) {
            return projectState != null && projectState.equals(obj);
        }
        // Match a LibraryState to a String by identity
        if (obj instanceof String) {
            return identity.toString().equals(obj.toString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity.hashCode(), getParentProjectState());
    }
}

