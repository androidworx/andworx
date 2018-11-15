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

import java.util.List;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andmore.internal.resources.manager.GlobalProjectMonitor.IProjectListener;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.registry.LibraryState;
import org.eclipse.andworx.registry.ProjectRegistrationListener;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class AndroidProjectListener implements IProjectListener, ProjectRegistrationListener {

	private Sdk sdk;
	private ProjectRegistry projectRegistry;

	public AndroidProjectListener(Sdk sdk) {
		this.sdk = sdk;
		this.projectRegistry = AndworxFactory.instance().getProjectRegistry();
	}
	
    @Override
    public void projectClosed(IProject project) {
        onProjectRemoved(project, false /*deleted*/);
    }

    @Override
    public void projectDeleted(IProject project) {
        onProjectRemoved(project, true /*deleted*/);
    }

    private void onProjectRemoved(IProject removedProject, boolean deleted) {
        // get the target project
        synchronized (Sdk.getLock()) {
            // Don't use getProject() as it could create the ProjectState if it's not
            // there yet and this is not what we want. We want the current object.
            // Therefore, direct access to the map.
            ProjectState removedState = projectRegistry.getProjectState(removedProject);
            if (removedState != null) {
                // now remove the project for the project map.
                projectRegistry.remove(removedProject);
            }
        }
    }

	@Override
	public void onProjectRemoved(ProjectState projectState, List<LibraryState> libraries) {
        // 1. Clear the layout lib cache associated with this project
     	sdk.clearCaches(projectState.getProject());
        // 2. if the project is a library, make sure to update the
        // LibraryState for any project referencing it.
        // Also, record the updated projects that are libraries, to update
        // projects that depend on them.
        for (LibraryState libState : libraries) {
            // Close the library right away.
            // This remove links between the LibraryState and the projectState.
            // This is because in case of a rename of a project, projectClosed and
            // projectOpened will be called before any other job is run, so we
            // need to make sure projectOpened is closed with the main project
            // state up to date.
        	sdk.closeLibrary(libState);
        }
	}
	
    @Override
    public void projectOpened(IProject project) {
        //onProjectOpened(project);
    }

    @Override
    public void projectOpenedWithWorkspace(IProject project) {
        // no need to force recompilation when projects are opened with the workspace.
        //onProjectOpened(project);
    }

    @Override
    public void allProjectsOpenedWithWorkspace() {
        // Correct currently open editors
        sdk.fixOpenLegacyEditors();
    }

    @Override
    public void onProjectOpened(final ProjectState openedState) {
        IProject openedProject = openedState.getProject();
        // Correct file editor associations.
        sdk.fixEditorAssociations(openedProject);
        // Fix classpath entries in a job since the workspace might be locked now.
        /*
        if (openedState.hasLibraries()) {
	        Job configureLibrariesJob = new Job("Configure Libraries") {
	            @Override
	            protected IStatus run(IProgressMonitor monitor) {
	                projectRegistry.configureLibraries(openedState);
	                return Status.OK_STATUS;
	            }
	        };
	
	        // Build jobs run only when there is no interactive activity
	        configureLibrariesJob.setPriority(Job.BUILD);
	        //configureLibrariesJob.setRule(ResourcesPlugin.getWorkspace().getRoot());
	        configureLibrariesJob.addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent event) {
		           	runFixClasspathJob(openedProject);
				}
	        });
	        configureLibrariesJob.schedule();
        } else { */
        	runFixClasspathJob(openedProject);
        //}
    }
 
	@Override
	public void onDependencyResolved(ProjectState projectState) {
        // There's a dependency! Add the project to the list of
        // modified project, but also to a list of projects
        // that saw one of its dependencies resolved.
        sdk.markProject(projectState, projectState.isLibrary());
	}

	@Override
	public void onLibraryFound(ProjectState projectState) {
        sdk.markProject(projectState, false /*updateParents*/);
	}

	@Override
	public void onBuildToolsStatus(ProjectState projectState, String status) {
	    sdk.handleBuildToolsMarker(projectState.getProject(), status.isEmpty() ? null : status);
	}

	@Override
    public void projectRenamed(IProject project, IPath from) {
        // we don't actually care about this anymore.
    }

    private void runFixClasspathJob(IProject openedProject) {
        // Fix classpath entries in a job since the workspace might be locked now.
        Job fixCpeJob = new Job("Adjusting Android Project Classpath") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
               try {
                	IJavaProject javaProject = JavaCore.create(openedProject);
                    ProjectHelper.fixProjectClasspathEntries(javaProject);  
                } catch (JavaModelException e) {
                    AndmoreAndroidPlugin.log(e, "error fixing classpath entries");
                    // Don't return e2.getStatus(); the job control will then produce
                    // a popup with this error, which isn't very interesting for the
                    // user.
                }

                return Status.OK_STATUS;
            }
        };

        // Build jobs run only when there is no interactive activity
        fixCpeJob.setPriority(Job.BUILD);
        fixCpeJob.setRule(ResourcesPlugin.getWorkspace().getRoot());
        fixCpeJob.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(IJobChangeEvent event) {
		        ProjectState openedState = projectRegistry.getProjectState(openedProject);
		        openedState.setOpenStatus(event.getResult());
				synchronized (openedState) {
					openedState.notifyAll();
				}
			}
        });
        fixCpeJob.schedule();
   }


}
