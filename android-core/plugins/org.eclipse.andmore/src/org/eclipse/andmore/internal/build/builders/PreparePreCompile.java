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

import static org.eclipse.core.resources.IncrementalProjectBuilder.FULL_BUILD;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.andmore.AdtUtils;
import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.Messages;
import org.eclipse.andmore.internal.build.RsSourceChangeHandler;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.resources.manager.IdeScanningContext;
import org.eclipse.andmore.internal.resources.manager.ProjectResources;
import org.eclipse.andmore.internal.resources.manager.ResourceManager;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IJavaProject;

import com.android.annotations.NonNull;
import com.android.sdklib.build.legacy.RenderScriptChecker;
import com.google.common.collect.Lists;

public class PreparePreCompile {

	private final PreCompilerContext builderContext;
	private final IProgressMonitor monitor;
	private final int kind;
	private String javaPackage;
	private String minSdkVersion;
    //private AidlProcessor aidlProcessor;
	private RsSourceChangeHandler rsSourceChangeHandler;
	private PreCompilerDeltaVisitor dv;
	
	public PreparePreCompile(PreCompilerContext builderContext, int kind, IProgressMonitor monitor) {
		this.builderContext = builderContext;
		this.kind = kind;
		this.monitor = monitor;
	}
	
	public String getJavaPackage() {
		return javaPackage;
	}

	public void setJavaPackage(String javaPackage) {
		this.javaPackage = javaPackage;
	}

	public String getMinSdkVersion() {
		return minSdkVersion;
	}

	public void setMinSdkVersion(String minSdkVersion) {
		this.minSdkVersion = minSdkVersion;
	}

	public PreCompilerDeltaVisitor getPreCompilerDeltaVisitor() {
		return dv;
	}
	
	public boolean prepareProject() throws AbortBuildException, CoreException {
        IProject project = builderContext.getProject();
        // Delay build if project target is being loaded
        try {
			AdtUtils.waitForTarget(project);
		} catch (InterruptedException e) {
			Thread.interrupted();
		}
        // get the project info
        ProjectState projectState = AndworxFactory.instance().getProjectState(project);
        List<Identity> pendingLibraries =  projectState.getPendingLibraries();
        if (!pendingLibraries.isEmpty()) {
        	boolean firstTime = true;
            StringBuffer msg = new StringBuffer("Project missing library dependencies: ");
            for (Identity identity: pendingLibraries) {
            	if (firstTime)
            		firstTime = false;
            	else
            		msg.append(", ");
            	msg.append(identity.getArtifactId());
            }
            AndmoreAndroidPlugin.printErrorToConsole(project, msg);
            builderContext.markProject(AndmoreAndroidConstants.MARKER_ADT, msg.toString(), IMarker.SEVERITY_ERROR);
        	return false;
        }

        // Get the libraries
        List<IProject> libProjects = new LinkedList<IProject>(projectState.getFullLibraryProjects());
        for (Iterator<IProject> iter = libProjects.iterator(); iter.hasNext();) {
            IProject libProject = iter.next();
            if (!libProject.isOpen()) {
                iter.remove();
            }
        }
        // TODO - Skip release version library projects
        //result = libProjects.toArray(new IProject[libProjects.size()]);
        IJavaProject javaProject = builderContext.getJavaProject();
        // now we need to get the classpath list
        List<IPath> sourceFolderPathList = BaseProjectHelper.getSourceClasspaths(javaProject);

        IFolder androidOutputFolder = BaseProjectHelper.getAndroidOutputFolder(project);
        setupSourceProcessors(sourceFolderPathList, androidOutputFolder);

        if (kind == FULL_BUILD) {
            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                    Messages.Start_Full_Pre_Compiler);

            if (builderContext.isDebugLog()) {
                AndmoreAndroidPlugin.log(IStatus.INFO, "%s full build!", project.getName());
            }

            // do some clean up.
            builderContext.cleanProject(monitor);
            return true;
        } 
        AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                Messages.Start_Inc_Pre_Compiler);

        // Go through the resources and see if something changed.
        // Even if the mCompileResources flag is true from a previously aborted
        // build, we need to go through the Resource delta to get a possible
        // list of aidl files to compile/remove.
        IResourceDelta delta = builderContext.getDelta(project);
        boolean mustCompileResources = builderContext.isMustCompileResources();
        boolean mustMergeManifest = builderContext.isMustMergeManifest();
        if (delta == null) {
        	// Do not automatically rebuild resources as this is costly
        	builderContext.setMustCompileResources(true);
        } else {
            dv = new PreCompilerDeltaVisitor((BaseBuilder)builderContext, sourceFolderPathList,
            		
            		rsSourceChangeHandler);
            delta.accept(dv);
            // Check to see if Manifest.xml, Manifest.java, or R.java have changed:
            mustCompileResources |= dv.getCompileResources();
            mustMergeManifest |= dv.hasManifestChanged();

            // Notify the ResourceManager:
            ResourceManager resManager = ResourceManager.getInstance();

            if (ResourceManager.isAutoBuilding()) {
                ProjectResources projectResources = resManager.getProjectResources(project);

                IdeScanningContext scanningContext = new IdeScanningContext(projectResources,
                        project, true);

                boolean wasCleared = projectResources.ensureInitialized();

                if (!wasCleared) {
                    resManager.processDelta(delta, scanningContext);
                }

                // Check whether this project or its dependencies (libraries) have
                // resources that need compilation
                if (wasCleared || scanningContext.needsFullAapt()) {
                    mustCompileResources = true;

                    // Must also call markAaptRequested on the project to not just
                    // store "aapt required" on this project, but also on any projects
                    // depending on this project if it's a library project
                    ResourceManager.markAaptRequested(project);
                }

                // Update error markers in the source editor
                if (!mustCompileResources) {
                	scanningContext.updateMarkers(false /* async */);
                }
            } // else: already processed the deltas in ResourceManager's IRawDeltaListener

            //aidlProcessor.doneVisiting(project);

            // get the java package from the visitor
            javaPackage = dv.getManifestPackage();
            minSdkVersion = dv.getMinSdkVersion();
        }
        // Has anyone marked this project as needing aapt? Typically done when
        // one of the library projects this project depends on has changed
        mustCompileResources |= ResourceManager.isAaptRequested(project);

        // if the main manifest didn't change, then we check for the library
        // ones (will trigger manifest merging too)
        // TODO - Skip release version libraries
        /*
        if (libProjects.size() > 0) {
            for (IProject libProject : libProjects) {
                IResourceDelta delta = getDelta(libProject);
                if (delta != null) {
                    PatternBasedDeltaVisitor visitor = new PatternBasedDeltaVisitor(
                            project, libProject,
                            "PRE:LibManifest"); //$NON-NLS-1$
                    visitor.addSet(ChangedFileSetHelper.MANIFEST);

                    ChangedFileSet textSymbolCFS = null;
                    if (isLibrary == false) {
                        textSymbolCFS = ChangedFileSetHelper.getTextSymbols(
                                libProject);
                        visitor.addSet(textSymbolCFS);
                    }

                    delta.accept(visitor);

                    mMustMergeManifest |= visitor.checkSet(ChangedFileSetHelper.MANIFEST);

                    if (textSymbolCFS != null) {
                        mMustCompileResources |= visitor.checkSet(textSymbolCFS);
                    }

                    // no need to test others if we have all flags at true.
                    if (mMustMergeManifest &&
                            (mMustCompileResources || textSymbolCFS == null)) {
                        break;
                    }
                }
            }
        }
        */
        /* TODO - Fix incremental build
        // Check the full resource package
        IResource tmp = androidOutputFolder.findMember(AndmoreAndroidConstants.FN_RESOURCES_AP_);
        if (tmp == null || tmp.exists() == false) {
        	mustCompileResources = true;
        }
     	*/
        // store the build status in the persistent storage
    	builderContext.setMustCompileResources(mustCompileResources);
    	builderContext.setMustMergeManifest(mustMergeManifest);
    	builderContext.setMustCreateBuildConfig(false);
    	//return true;
    	return false;
	}

    private void setupSourceProcessors(
            @NonNull List<IPath> sourceFolderPathList,
            @NonNull IFolder androidOutputFolder) throws AbortBuildException {
    	/*
    	aidlProcessor = builderContext.initializeAidl(kind == FULL_BUILD); 
        */
        List<File> sourceFolders = Lists.newArrayListWithCapacity(sourceFolderPathList.size());
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

        for (IPath path : sourceFolderPathList) {
            IResource resource = root.findMember(path);
            if (resource != null && resource.exists() && resource.getType() == IResource.FOLDER) {
                IPath fullPath = resource.getLocation();
                if (fullPath != null) {
                    sourceFolders.add(fullPath.toFile());
                }
            }
        }

        RenderScriptChecker checker = new RenderScriptChecker(sourceFolders,
                androidOutputFolder.getLocation().toFile());
        rsSourceChangeHandler = builderContext.initializeRenderScript(checker, kind == FULL_BUILD);
    }
}
