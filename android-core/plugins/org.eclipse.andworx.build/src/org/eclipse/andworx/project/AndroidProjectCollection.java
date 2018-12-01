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
package org.eclipse.andworx.project;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andmore.base.JavaProjectHelper;
import org.eclipse.andworx.AndworxConstants;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Container for Android Projects
 */
public class AndroidProjectCollection {
    /**
     * Project filter to be used with {@link BaseProjectHelper#getAndroidProjects(IProjectFilter)}.
     */
    public static interface IProjectFilter {
        boolean accept(IProject project);
    }

    /**
     * Interface to filter out some project displayed by {@link ProjectChooserHelper}.
     *
     * @see IProjectFilter
     */
    public interface IProjectChooserFilter extends IProjectFilter {
        /**
         * Whether the Project Chooser can compute the project list once and cache the result.
         * </p>If false the project list is recomputed every time the dialog is opened.
         */
        boolean useCache();
    }

    private final IProjectChooserFilter projectFilter;
    private final JavaProjectHelper javaProjectHelper;
    /**
     * List of current android projects. Since the dialog is modal, we'll just get
     * the list once on-demand.
     */
    private IJavaProject[] projects;
    
    public AndroidProjectCollection(IProjectChooserFilter projectFilter) {
    	this.projectFilter = projectFilter;
        javaProjectHelper = BasePlugin.getBaseContext().getJavaProjectHelper();
    }

    public int size() {
    	return getAndroidProjects().length;
    }
    
    /**
     * Returns the list of Android projects.
     * <p/>
     * Because this list can be time consuming, this class caches the list of project.
     * It is recommended to call this method instead of
     * {@link BaseProjectHelper#getAndroidProjects()}.
     * @return IJavaProject array
     */
    public IJavaProject[] getAndroidProjects() {
        // recompute only if we don't have the projects already or the filter is dynamic
        // and prevent usage of a cache.
        if (projects == null || (projectFilter != null && projectFilter.useCache() == false)) {
        	IJavaProject[] projectCollection = javaProjectHelper.getProjectsByNature(AndworxConstants.ANDROID_NATURE);
        	if (projectFilter != null) {
            	List<IJavaProject> androidProjects = new ArrayList<>();
	            for (IJavaProject project: projectCollection) {
	                if (projectFilter.accept(project.getProject())) 
	                	androidProjects.add(project);
	            }
	            projects = androidProjects.toArray(new IJavaProject[androidProjects.size()]);
            }
        	else
        		projects = projectCollection;
         }
         return projects;
    }

    /**
     * Helper method to get the Android project with the given name
     *
     * @param projectName the name of the project to find
     * @return the {@link IProject} for the Android project. <code>null</code> if not found.
     */
    public IProject getAndroidProject(String projectName) {
        IProject projectResource = null;
        IJavaProject[] javaProjects = getAndroidProjects();
        if (javaProjects != null) {
            for (IJavaProject javaProject : javaProjects) {
                if (javaProject.getElementName().equals(projectName)) {
                	projectResource = javaProject.getProject();
                    break;
                }
            }
        }
        return projectResource;
    }
}
