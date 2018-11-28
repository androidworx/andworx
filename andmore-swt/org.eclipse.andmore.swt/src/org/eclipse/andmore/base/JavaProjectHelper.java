/**
 * Copyright (C) 2007, 2015 The Android Open Source Project
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
package org.eclipse.andmore.base;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.viewers.ILabelProvider;

public class JavaProjectHelper {

    /**
     * Returns the {@link IFolder} representing the output for the project for Android specific
     * files.
     * <p>
     * The project must be a java project and be opened, or the method will return null.
     * @param project the {@link IProject}
     * @return an IFolder item or null.
     */
    public final IFolder getJavaOutputFolder(IProject project) {
        try {
            if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
                // get a java project from the normal project object
                IJavaProject javaProject = getJavaProject(project);
                IPath path = javaProject.getOutputLocation();
                path = path.removeFirstSegments(1);
                return project.getFolder(path);
            }
        } catch (JavaModelException e) {
            // Let's do nothing and return null
        } catch (CoreException e) {
            // Let's do nothing and return null
        }
        return null;
    }

    public IJavaModel getJavaModel() {
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        return JavaCore.create(workspaceRoot);
    }
    
    public ILabelProvider getJavaElementLabelProvider() {
    	return new JavaElementLabelProvider();
    }
    
    public IJavaProject getJavaProject(IProject project) {
    	return JavaCore.create(project);
    }
}
