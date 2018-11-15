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

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.preferences.AdtPrefs.BuildVerbosity;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.task.BuildConfigTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;

import com.android.builder.compiling.BuildConfigGenerator;
import com.android.utils.FileUtils;

/**
 * Task to generate buildConfig.java
 * Project location obtained from context.getGenManifestPackageFolder()
 * Build location obtained from variantContext.getBuildConfigSourceOutputDir()
 *
 */
public class BuildConfigOp implements BuildOp<PreCompilerContext> {

	/** Directory destination on commit */
	private File destinationDir;
	private BuildConfigTask buildConfigTask;
	
	public BuildConfigOp() {
	}
	
	@Override
	public boolean execute(PreCompilerContext context) throws CoreException, InterruptedException {
		IFolder projectFolder = projectFileLocation(context);
        destinationDir = projectFolder.getLocation().toFile();
        VariantContext variantScope = context.getVariantContext();
        AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        boolean debugMode = variantConfig.getBuildType().isDebuggable();
        IProject project = context.getProject();
        boolean isMustCreateBuildConfig = context.isMustCreateBuildConfig();
    	Path projectFilePath = new Path(BuildConfigGenerator.BUILD_CONFIG_NAME);
        if (!isMustCreateBuildConfig) {
            // Check the file is present in the project
            if (!projectFolder.exists(projectFilePath)) {
            	isMustCreateBuildConfig = true;
                AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        String.format("Class %1$s is missing!",
                                BuildConfigGenerator.BUILD_CONFIG_NAME));
            } else if (debugMode != context.getLastBuildConfigMode()) {
                // else if the build mode changed, force creation
            	isMustCreateBuildConfig = true;
                AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        String.format("Different build mode, must update %1$s!",
                                BuildConfigGenerator.BUILD_CONFIG_NAME));
            }
        }

        if (isMustCreateBuildConfig) {
            if (context.isDebugLog()) {
                AndmoreAndroidPlugin.log(IStatus.INFO, "%s generating BuilderConfig!", project.getName());
            }

            AndmoreAndroidPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                    String.format("Generating %1$s...", BuildConfigGenerator.BUILD_CONFIG_NAME));
            buildConfigTask = AndworxFactory.instance().getBuildConfigTask(context.getManifestPackage(), variantScope);
            buildConfigTask.schedule();
            TaskFactory taskFactory = context.getTaskFactory();
        	synchronized(taskFactory) {
        		taskFactory.wait();
        	}
        }
        context.saveLastBuildConfigMode(debugMode);
		return true;
	}

	@Override
	public void commit(PreCompilerContext context) throws IOException {
        context.saveMustCreateBuildConfig(false);
		if (buildConfigTask == null) // No BuildConfig.java generated
			return;
        // Prepare for commit by deleting old file from project. Does not delete file on file system.
        //if (projectFolder.exists(projectFilePath)) {
        //	IFile toDelete = projectFolder.getFile(BuildConfigGenerator.BUILD_CONFIG_NAME);
        //	toDelete.delete(true, monitor);
        //}
        // Copy build file to project
		File oldFile = new File(destinationDir, BuildConfigGenerator.BUILD_CONFIG_NAME);
		if (oldFile.exists())
			oldFile.delete();
		else if (!destinationDir.exists() && !destinationDir.mkdirs())
			throw new IOException("Failed to create path " + destinationDir.toString());
		FileUtils.copyFile(buildConfigTask.getNewFile(), oldFile);
 	}

	@Override
	public String getDescription() {
		return BuildConfigTask.TASK_NAME;
	}

	private IFolder projectFileLocation(PreCompilerContext context) throws CoreException {
		return context.getGenManifestPackageFolder();
	}
}
