/*
 * Copyright (C) 2007 The Android Open Source Project
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
package org.eclipse.andmore.internal.build.builders;

import java.util.List;

import org.eclipse.andmore.AdtUtils;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.BuildHelper;
import org.eclipse.andmore.internal.build.Messages;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.lint.LintDeltaProcessor;
import org.eclipse.andmore.internal.preferences.AdtPrefs;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.ProjectHelper;
import org.eclipse.andmore.internal.resources.manager.ResourceManager;
import org.eclipse.andmore.internal.sdk.Sdk;
import org.eclipse.andmore.internal.sdk.Sdk.ITargetChangeListener;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.sdk.TargetLoadStatusMonitor;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaProject;

import com.android.ide.common.sdk.LoadStatus;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.IAndroidTarget;

public class PreparePostCompile {
    private static class TargetChangeListener implements ITargetChangeListener {

    	// Target identifier
    	String hashString;
    	IProject project;
    	
    	public TargetChangeListener(IProject project, String hashString) {
    		this.hashString = hashString;
    		this.project = project;
    	}
    	
		@Override
		public void onProjectTargetChange(IProject changedProject) {
            if (changedProject != null && changedProject.equals(project)) 
            	// Assume change to loaded target
				synchronized(this) {
					notifyAll();
				}
		}

		@Override
		public void onTargetLoaded(IAndroidTarget target) {
			if (hashString.equals(AndroidTargetHash.getTargetHashString(target)))
				synchronized(this) {
					notifyAll();
				}
		}

		@Override
		public void onSdkLoaded() {
			// This is not expected
			synchronized(this) {
				notifyAll();
			}
		}
	}


	private final PostCompilerContext builderContext;
	private final IProgressMonitor monitor;
	private final int kind;

	public PreparePostCompile(PostCompilerContext builderContext, int kind, IProgressMonitor monitor) {
		this.builderContext = builderContext;
		this.kind = kind;
		this.monitor = monitor;
	}
	
	public boolean prepareProject() throws AbortBuildException, CoreException {
        IProject project = builderContext.getProject();
        // Delay build if project target is being loaded
        try {
			AdtUtils.waitForTarget(project);
		} catch (InterruptedException e) {
			Thread.interrupted();
		}
        if (builderContext.isDebugLog()) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "%s BUILD(POST)", project.getName());
        }

        if (BuildHelper.BENCHMARK_FLAG) {
            // End JavaC Timer
            String msg = "BENCHMARK ADT: Ending Compilation \n BENCHMARK ADT: Time Elapsed: " +    //$NON-NLS-1$
                         (System.nanoTime() - BuildHelper.sStartJavaCTime)/Math.pow(10, 6) + "ms"; //$NON-NLS-1$
            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
            msg = "BENCHMARK ADT: Starting PostCompilation";                                       //$NON-NLS-1$
            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, project, msg);
            builderContext.setStartBuildTime(System.nanoTime());
        }
        return true;
	}

	public void setBuildFlags() throws CoreException {
        IProject project = builderContext.getProject();
		if (kind == PostCompilerBuilder.FULL_BUILD) {
            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                    Messages.Start_Full_Apk_Build);

            if (builderContext.isDebugLog()) {
                AndmoreAndroidPlugin.log(IStatus.INFO, "%s full build!", project.getName());
            }

            // Full build: we do all the steps.
            builderContext.saveConvertToDex(true);
            builderContext.saveBuildFinalPackage(true);
            return;
        } 
        AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                Messages.Start_Inc_Apk_Build);

        // Go through the resources and see if something changed.
        IResourceDelta delta = builderContext.getDelta(project);
        if (delta == null) {
            // no delta? Same as full build: we do all the steps.
            builderContext.saveConvertToDex(true);
            builderContext.saveBuildFinalPackage(true);
            return;
        }
		AdtPrefs adtPrefs = AndmoreAndroidPlugin.getDefault().getAdtPrefs();
        if (ResourceManager.isAutoBuilding() && adtPrefs.isLintOnSave()) {
            // Check for errors on save/build, if enabled
            LintDeltaProcessor.create().process(delta);
        }

        PatternBasedDeltaVisitor dv = new PatternBasedDeltaVisitor(
                project, project,
                "POST:Main");

        ChangedFileSet manifestCfs = ChangedFileSetHelper.getMergedManifestCfs(project);
        dv.addSet(manifestCfs);

        ChangedFileSet resCfs = ChangedFileSetHelper.getResCfs(project);
        dv.addSet(resCfs);

        ChangedFileSet androidCodeCfs = ChangedFileSetHelper.getCodeCfs(project);
        dv.addSet(androidCodeCfs);

        ChangedFileSet javaResCfs = ChangedFileSetHelper.getJavaResCfs(project);
        dv.addSet(javaResCfs);
        dv.addSet(ChangedFileSetHelper.NATIVE_LIBS);

        delta.accept(dv);

    	boolean buildFinalPackage = builderContext.isBuildFinalPackage();
    	boolean convertToDex = builderContext.isConvertToDex();
        // save the state
        //packageResources |= dv.checkSet(manifestCfs) || dv.checkSet(resCfs);

        convertToDex |= dv.checkSet(androidCodeCfs);

        buildFinalPackage |= dv.checkSet(javaResCfs) ||
                dv.checkSet(ChangedFileSetHelper.NATIVE_LIBS);

	    // check the libraries
        // Get the project info
        ProjectState projectState = AndworxFactory.instance().getProjectState(project);
        List<IProject> libProjects = projectState.getFullLibraryProjects();
	    if (libProjects.size() > 0) {
	        for (IProject libProject : libProjects) {
	            delta = builderContext.getDelta(libProject);
	            if (delta != null) {
	                PatternBasedDeltaVisitor visitor = new PatternBasedDeltaVisitor(
	                        project, libProject,
	                        "POST:Lib");
	
	                ChangedFileSet libResCfs = ChangedFileSetHelper.getFullResCfs(
	                        libProject);
	                visitor.addSet(libResCfs);
	                visitor.addSet(ChangedFileSetHelper.NATIVE_LIBS);
	                // FIXME: add check on the library.jar?
	
	                delta.accept(visitor);
	
//	                packageResources |= visitor.checkSet(libResCfs);
	                buildFinalPackage |= visitor.checkSet(
	                        ChangedFileSetHelper.NATIVE_LIBS);
	            }
	        }
	    }
	
	    // Also go through the delta for all the referenced projects
        List<IProject> javaProjects = ProjectHelper.getReferencedProjects(project);
        List<IJavaProject> referencedJavaProjects = 
        		BuildHelper.getJavaProjects(javaProjects);
	    final int referencedCount = referencedJavaProjects.size();
	    for (int i = 0 ; i < referencedCount; i++) {
	        IJavaProject referencedJavaProject = referencedJavaProjects.get(i);
	        delta = builderContext.getDelta(referencedJavaProject.getProject());
	        if (delta != null) {
	            IProject referencedProject = referencedJavaProject.getProject();
	            PatternBasedDeltaVisitor visitor = new PatternBasedDeltaVisitor(
	                    project, referencedProject,
	                    "POST:RefedProject");
	
	            javaResCfs = ChangedFileSetHelper.getJavaResCfs(referencedProject);
	            visitor.addSet(javaResCfs);
	
	            ChangedFileSet bytecodeCfs = ChangedFileSetHelper.getByteCodeCfs(referencedProject);
	            visitor.addSet(bytecodeCfs);
	
	            delta.accept(visitor);
	
	            // save the state
	            convertToDex |= visitor.checkSet(bytecodeCfs);
	            buildFinalPackage |= visitor.checkSet(javaResCfs);
	        }
	    }
	    builderContext.saveConvertToDex(convertToDex);
	    builderContext.saveBuildFinalPackage(buildFinalPackage);
	}
	
}
