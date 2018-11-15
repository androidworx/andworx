/*
 * Copyright (C) 2015 David Carver
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

package org.eclipse.andmore.android.command;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.internal.build.builders.PostCompilerBuilder;
import org.eclipse.andmore.internal.build.builders.PreCompilerBuilder;
import org.eclipse.andmore.internal.build.builders.ResourceManagerBuilder;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;


public class ConvertADTProject extends AbstractHandler {

	private List<IProject> projectList = null;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Retrieve the selection used in the command
		IWorkbench workbench = PlatformUI.getWorkbench();
		if ((workbench == null) || workbench.isClosing()) {
			return null;
		}

		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		if (window == null) {
			return null;
		}

		ISelection selection = window.getSelectionService().getSelection();
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection sselection = (IStructuredSelection) selection;
			Iterator<?> it = sselection.iterator();

			// Construct a list of valid projects to be cleaned
			projectList = new ArrayList<IProject>(sselection.size());

			while (it.hasNext()) {
				Object resource = it.next();

				// Check if the selected item is a project
				if (resource instanceof IJavaProject) {
					IJavaProject javaProject = (IJavaProject) resource;
					projectList.add(javaProject.getProject());
				} else if (resource instanceof IAdaptable) {
					IAdaptable adaptable = (IAdaptable) resource;
					projectList.add((IProject) adaptable.getAdapter(IProject.class));
				}
			}
			convertProjectsToAndmore();
		}

		return null;
	}

	protected void convertProjectsToAndmore() {
		// Probably should do a job here but for now we'll do everything in the
		// command.
		for (IProject project : projectList) {
			try {
				if (!project.hasNature(AndmoreAndroidConstants.ADT_NATURE)) {
					break;
				}
				IJavaProject androidProject = JavaCore.create(project);
				
				updateProjectDescription(androidProject);
				updateClasspathEntries(androidProject);

			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void updateClasspathEntries(IJavaProject androidProject) throws JavaModelException {
		ArrayList<IClasspathEntry> newclasspathEntries = new ArrayList<IClasspathEntry>();

		IClasspathEntry[] classpathEntries = androidProject.getRawClasspath();
		for (IClasspathEntry classpathEntry : classpathEntries) {
			if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
				String classpathId = classpathEntry.getPath().toString();
				if (classpathId.equals(AndmoreAndroidConstants.ADT_CONTAINER_DEPENDENCIES)) {
					newclasspathEntries
							.add(createNewAndmoreContainer(AndmoreAndroidConstants.CONTAINER_DEPENDENCIES));
				} else if (classpathId.equals(AndmoreAndroidConstants.ADT_CONTAINER_FRAMEWORK)) {
					newclasspathEntries
							.add(createNewAndmoreContainer(AndmoreAndroidConstants.CONTAINER_FRAMEWORK));
				} else if (classpathId.equals(AndmoreAndroidConstants.ADT_CONTAINER_PRIVATE_LIBRARIES)) {
					newclasspathEntries
							.add(createNewAndmoreContainer(AndmoreAndroidConstants.CONTAINER_PRIVATE_LIBRARIES));
				} else {
					newclasspathEntries.add(classpathEntry);
				}

			} else {
				newclasspathEntries.add(classpathEntry);
			}
		}

		IClasspathEntry[] andmoreClasspathEntries = new IClasspathEntry[newclasspathEntries.size()];
		newclasspathEntries.toArray(andmoreClasspathEntries);
		androidProject.setRawClasspath(andmoreClasspathEntries, true, new NullProgressMonitor());

	}

	private void updateProjectDescription(IJavaProject androidProject) throws CoreException {
		IProjectDescription description = androidProject.getProject().getDescription();
		description.setNatureIds(new String[] { AndmoreAndroidConstants.NATURE_DEFAULT, JavaCore.NATURE_ID });
		
		ICommand commands[] = description.getBuildSpec();
		ArrayList<ICommand> acutalCommands = new ArrayList<ICommand>();
		for (ICommand command : commands) {
			if (!command.getBuilderName().equals("com.android.ide.eclipse.adt.PreCompilerBuilder") &&
				!command.getBuilderName().equals("com.android.ide.eclipse.adt.ResourceManagerBuilder") &&
				!command.getBuilderName().equals("com.android.ide.eclipse.adt.ApkBuilder")) {
				acutalCommands.add(command);
			}
		}
		
		ICommand keepCommands[] = new ICommand[acutalCommands.size()];
		acutalCommands.toArray(keepCommands);
		
		description.setBuildSpec(keepCommands);
		
		description.setBuildConfigs(new String[] { ResourceManagerBuilder.ID, PostCompilerBuilder.ID,
				PreCompilerBuilder.ID, JavaCore.BUILDER_ID });
		
		androidProject.getProject().setDescription(description, new NullProgressMonitor());
	}

	private IClasspathEntry createNewAndmoreContainer(String id) {
		IClasspathEntry andmoreClasspathEntry = JavaCore.newContainerEntry(new Path(id));
		return andmoreClasspathEntry;
	}

}
