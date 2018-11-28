/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andmore.internal.build.builders;

import static com.android.SdkConstants.EXT_ANDROID_PACKAGE;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andmore.internal.project.ApkInstallManager;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.build.task.PackageApplicationTask;
import org.eclipse.andworx.context.MultiOutputPolicy;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.android.SdkConstants;
import com.android.utils.FileUtils;

/**
 * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
 *
 * Requires fullBuildInfoGeneratorTask task that generates the build-info.xml for full build.
 */
public class PackageApplicationOp implements BuildOp<PostCompilerContext> {

	private final File targetDirectory;
	private File outputFile;
	private IProgressMonitor monitor;
	
	public PackageApplicationOp(File targetDirectory, IProgressMonitor monitor) {
		this.targetDirectory = targetDirectory;
		this.monitor = monitor;
	}

	@Override
	public boolean execute(PostCompilerContext context) throws CoreException, AbortBuildException, InterruptedException {
        VariantContext variantScope = context.getVariantContext();
        //boolean signedApk = variantScope.getVariantConfiguration().isSigningReady();
        OutputType manifestType = OutputType.MERGED_MANIFESTS;
        Collection<File> manifests = variantScope.getOutput(manifestType);
        final boolean splitsArePossible = 
        		variantScope.getMultiOutputPolicy() == MultiOutputPolicy.SPLITS;
        OutputType outputType = 
                splitsArePossible ?
                OutputType.FULL_APK :
                OutputType.APK;
        OutputType resourceFilesInputType =
                variantScope.useResourceShrinker() ?
                OutputType.SHRUNK_PROCESSED_RES :
                OutputType.PROCESSED_RES;
        Set<File> dexFiles = new HashSet<>();
        context.getPipelineInput().forEach(input -> 
        	dexFiles.add(
        			input.getDirectoryInputs().iterator().next().getFile()));
        String taskName = variantScope.getTaskName("package", EXT_ANDROID_PACKAGE);
        File incrementalFolder = new File(variantScope.getIncrementalDir(taskName), "tmp");
        AndworxContext objectFactory = AndworxFactory.instance(); 
        PackageApplicationTask packageApkTask = objectFactory.getPackageApplicationTask(variantScope);
        ProjectState projectState = objectFactory.getProjectState(context.getProject());
        outputFile = packageApkTask.configure(
    		    resourceFilesInputType,
                manifests,
                manifestType,
                dexFiles,
                incrementalFolder,
                projectState.createdBy());
        variantScope.addOutput(outputType, Collections.singletonList(outputFile.getParentFile()), packageApkTask.getTaskName());
        // Prepare for commit by deleting old file from project. Does not delete file on file system.
    	IFolder projectFolder = context.getProject().getFolder(targetDirectory.getName());
    	String filename = getApkFilename(context.getProject());
        if (projectFolder.exists(new org.eclipse.core.runtime.Path(filename))) {
        	IFile toDelete = projectFolder.getFile(filename);
        	toDelete.delete(true, monitor);
        }
        packageApkTask.schedule();
        TaskFactory taskFactory = context.getTaskFactory();
    	synchronized(taskFactory) {
    		taskFactory.wait();
    	}
		return true;
	}

	@Override
	public void commit(PostCompilerContext context) throws IOException {
		if (outputFile == null) // This is not expected
			throw new IllegalStateException("Output file is null");
        File oldFile = new File(targetDirectory, getApkFilename(context.getProject()));
		if (oldFile.exists())
			oldFile.delete();
		FileUtils.copyFile(outputFile, oldFile);
        context.saveBuildFinalPackage(false);

        // Reset the installation manager to force new installs of this project
        ApkInstallManager.getInstance().resetInstallationFor(context.getProject());

        AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, context.getProject(), "Build Success!");
	}

	@Override
	public String getDescription() {
		return "Package APK";
	}

	private String getApkFilename(IProject project) {
		return project.getName() + SdkConstants.DOT_ANDROID_PACKAGE;
	}
	

}
