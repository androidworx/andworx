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

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.Messages;
import org.eclipse.andmore.internal.build.SourceProcessor;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andmore.internal.resources.manager.ResourceManager;
import org.eclipse.andworx.aapt.MergeType;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.build.task.MergeResourcesTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import com.android.SdkConstants;

public class ResourcesOp implements BuildOp<PreCompilerContext> {

	private final IProgressMonitor monitor;
	
	public ResourcesOp(IProgressMonitor monitor) {
		this.monitor = monitor;
	}
	
	@Override
	public boolean execute(PreCompilerContext context) throws CoreException, AbortBuildException, InterruptedException {
		return processResources(context);
	}

	@Override
	public void commit(PreCompilerContext context) throws IOException {
        ResourceManager.clearAaptRequest(context.getProject());
        IProject project = context.getProject();
    	try {
            // Prepare for commit by deleting old R.java files from project. Does not delete files on file system.
    		IFolder sourceFolder = context.getGenFolder();
            if (sourceFolder.exists()) {
            	IResource[] sourceMembers = sourceFolder.members(IResource.NONE);
    			for (IResource resource: sourceMembers) {
    				int type = resource.getType();
    				if ((type == IResource.FILE) || (type == IResource.FOLDER))
    					resource.delete(true, monitor);
    		        }
    	    }
    		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		} catch (CoreException e) {
			throw new IOException(project.getLocation().toOSString(), e);
		}
        IFolder projectFolder = BaseProjectHelper.getAndroidOutputFolder(context.getProject());
        // Prepare for commit by deleting the old R.txt file from project. Does not delete file on file system.
        if (projectFolder.exists(new org.eclipse.core.runtime.Path(SdkConstants.FN_RESOURCE_TEXT))) {
        	IFile toDelete = projectFolder.getFile(SdkConstants.FN_RESOURCE_TEXT);
        	try {
				toDelete.delete(true, monitor);
			} catch (CoreException e) {
				throw new IOException(toDelete.getLocation().toOSString(), e);
			}
        }
		//FileUtils.copyDirectoryContentToDirectory(outputSourceLocation(context), context.getGenFolder().getLocation().toFile());
	}

	@Override
	public String getDescription() {
		return "Resources processing";
	}
	
	//private File outputSourceLocation(PreCompilerContext context) {
	//	return context.getVariantContext().getProcessResourcePackageOutputDirectory();
	//}

	private boolean processResources(PreCompilerContext context) throws CoreException, AbortBuildException, InterruptedException {
        // if a processor created some resources file, force recompilation of the resources.
		boolean mustCompileResources = context.isMustCompileResources();
        if ((context.getSourceProcessorStatus() & SourceProcessor.COMPILE_STATUS_RES) != 0) {
        	mustCompileResources = true;
            // save the current state before attempting the compilation
            context.saveMustCompileResources(true);
        }

        IProject project = context.getProject();
       // handle the resources, after the processors are run since some (renderscript)
        // generate resources.
        boolean compiledTheResources = mustCompileResources;
         if (mustCompileResources) {
            ProjectState projectState = AndworxFactory.instance().getProjectState(project);
            if (context.isDebugLog()) {
                 AndmoreAndroidPlugin.log(IStatus.INFO, "%s compiling resources!", project.getName());
            }
            // get the manifest file
            IFile manifestFile = project.getFile(projectState.getProjectSourceFolder(CodeSource.manifest));
            String resDir = projectState.getProjectSourceFolder(CodeSource.res);
            if (!resDir.endsWith("/"))
            	resDir = resDir + "/";
            IFolder resFolder = project.getFolder(resDir);
        	handleResources(
        		context, 
        		project,
        		manifestFile, 
        		resFolder);
            TaskFactory taskFactory = context.getTaskFactory();
        	synchronized(taskFactory) {
        		taskFactory.wait();
        	}
        	return true;
        }

        if (context.getSourceProcessorStatus() == SourceProcessor.COMPILE_STATUS_NONE &&
                compiledTheResources == false) {
            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                    Messages.Nothing_To_Compile);
        }
        return false;
	}

    /**
     * Handles resource changes and regenerate whatever files need regenerating.
     * @param manifest the {@link IFile} representing the project manifest
     * @param resOutFolder ?
     * @throws CoreException
     * @throws AbortBuildException
     */
    private void handleResources(PreCompilerContext context, IProject project, IFile manifest, IFolder resFolder) throws CoreException, AbortBuildException {
        // remove the aapt markers
        context.removeMarkersFromResource(manifest, AndmoreAndroidConstants.MARKER_AAPT_COMPILE);
        context.removeMarkersFromContainer(resFolder, AndmoreAndroidConstants.MARKER_AAPT_COMPILE);

        AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                Messages.Preparing_Generated_Files);
        createMergeResourcesTask(context.getVariantContext());
    }

	private void createMergeResourcesTask(VariantContext variantScope) {
        File mergedOutputDir = variantScope.getDefaultMergeResourcesOutputDir();
        boolean alsoOutputNotCompiledResources =
        		variantScope.useResourceShrinker()
                        /*|| variantScope.getTestedVariantData() == null
                                && globalScope
                                        .getExtension()
                                        .getTestOptions()
                                        .getUnitTests()
                                        .isIncludeAndroidResources())*/;
        File mergedNotCompiledDir =
                alsoOutputNotCompiledResources
                        ? new File(
                        		variantScope.getIntermediatesDir()
                                        + "/merged-not-compiled-resources/"
                                        + variantScope.getVariantConfiguration().getDirName())
                        : null;

		MergeResourcesTask mergeTask = AndworxFactory.instance().getMergeResourcesTask(variantScope);
		mergeTask.configure(
				mergedOutputDir, 
				mergedNotCompiledDir, 
				true, 
				true);
        variantScope.addOutput(
                MergeType.MERGE.getOutputType(), Collections.singletonList(mergedOutputDir), MergeResourcesTask.TASK_NAME);

        if (alsoOutputNotCompiledResources) {
        	variantScope.addOutput(
                    OutputType.MERGED_NOT_COMPILED_RES, Collections.singletonList(mergedNotCompiledDir), MergeResourcesTask.TASK_NAME);
        }
		mergeTask.schedule();
	}
}
