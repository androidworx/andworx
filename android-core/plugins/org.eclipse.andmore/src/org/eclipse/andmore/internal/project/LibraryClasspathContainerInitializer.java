/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.eclipse.andmore.internal.project;

import java.util.List;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

public class LibraryClasspathContainerInitializer extends BaseClasspathContainerInitializer {

    private static class LibraryClasspathHelperHolder {
    	private volatile LibraryClasspathHelper helper;
    	
    	public LibraryClasspathHelper getHelper() {
    		if (helper == null) {
    			synchronized (this) {
    				if (helper == null) {
    					helper = new LibraryClasspathHelper();
    				}
    			}
    		}
    		return helper;
    	}
    }

    private static LibraryClasspathHelperHolder libraryClasspathHelperHolder = new LibraryClasspathHelperHolder();
    
    public LibraryClasspathContainerInitializer() {
    }

    /**
     * Updates the {@link IJavaProject} objects with new library.
     * @param androidProjects the projects to update.
     * @return <code>true</code> if success, <code>false</code> otherwise.
     */
    public static boolean updateProjects(IJavaProject[] androidProjects) {
    	return libraryClasspathHelperHolder.getHelper().updateProjects(androidProjects);
    }

    /**
     * Updates the {@link IJavaProject} objects with new library.
     * @param androidProjects the projects to update.
     * @return <code>true</code> if success, <code>false</code> otherwise.
     */
    public static boolean updateProject(List<ProjectState> projects) {
        return libraryClasspathHelperHolder.getHelper().updateProject(projects);
    }

    @Override
    public void initialize(IPath containerPath, IJavaProject project) throws CoreException {
    	LibraryClasspathHelper helper = libraryClasspathHelperHolder.getHelper();
        if (AndmoreAndroidConstants.CONTAINER_PRIVATE_LIBRARIES.equals(containerPath.toString())) {
            IClasspathContainer libraries = helper.allocateLibraryContainer(project);
            if (libraries != null) {
                JavaCore.setClasspathContainer(new Path(AndmoreAndroidConstants.CONTAINER_PRIVATE_LIBRARIES),
                        new IJavaProject[] { project },
                        new IClasspathContainer[] { libraries },
                        new NullProgressMonitor());
            }

        } else if(AndmoreAndroidConstants.CONTAINER_DEPENDENCIES.equals(containerPath.toString())) {
            IClasspathContainer dependencies = helper.allocateDependencyContainer(project);
            if (dependencies != null) {
                JavaCore.setClasspathContainer(new Path(AndmoreAndroidConstants.CONTAINER_DEPENDENCIES),
                        new IJavaProject[] { project },
                        new IClasspathContainer[] { dependencies },
                        new NullProgressMonitor());
            }
        }
    }

 }
