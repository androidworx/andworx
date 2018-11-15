/*
 * Copyright (C) 2008 The Android Open Source Project
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
package org.eclipse.andmore.internal.sdk;

import static com.android.SdkConstants.EXT_JAR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor.IFileListener;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

public class ProjectFileListener implements IFileListener{

	public ProjectFileListener(Sdk sdk) {
	}
	
    @Override
    public void fileChanged(final @NonNull IFile file, @NonNull IMarkerDelta[] markerDeltas,
            int kind, @Nullable String extension, int flags, boolean isAndroidProject) {
        if (isAndroidProject) {
            if (kind == IResourceDelta.ADDED || kind == IResourceDelta.REMOVED) {
	            // check if it's an add/remove on a jar files inside libs
	            if (EXT_JAR.equals(extension) &&
	                    file.getProjectRelativePath().segmentCount() == 2 &&
	                    file.getParent().getName().equals(SdkConstants.FD_NATIVE_LIBS)) {
	                // need to update the project and whatever depend on it.
	               processJarFileChange(file);
	            }
	        }
        }
    }

    private void processJarFileChange(final IFile file) {
        try {
            IProject iProject = file.getProject();
            if (iProject.hasNature(AndmoreAndroidConstants.NATURE_DEFAULT) == false) {
                return;
            }
            List<IJavaProject> projectList = new ArrayList<IJavaProject>();
            IJavaProject javaProject = BaseProjectHelper.getJavaProject(iProject);
            if (javaProject != null) {
                projectList.add(javaProject);
            }
            ProjectState state = AndworxFactory.instance().getProjectState(iProject);
            if (state != null) {
                Collection<ProjectState> parents = state.getFullParentProjects();
                for (ProjectState s : parents) {
                    javaProject = BaseProjectHelper.getJavaProject(s.getProject());
                    if (javaProject != null) {
                        projectList.add(javaProject);
                    }
                }
                ProjectHelper.updateProjects(
                        projectList.toArray(new IJavaProject[projectList.size()]));
            }
        } catch (CoreException e) {
            // This can't happen as it's only for closed project (or non existing)
            // but in that case we can't get a fileChanged on this file.
        }
    }
}
