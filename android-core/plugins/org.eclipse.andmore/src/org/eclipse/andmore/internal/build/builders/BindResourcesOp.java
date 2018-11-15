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
package org.eclipse.andmore.internal.build.builders;

import java.io.File;
import java.io.IOException;

import org.eclipse.andmore.AndmoreAndroidConstants;
import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andworx.aapt.MergeType;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.task.NonNamespacedLinkResourcesTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import com.android.SdkConstants;
import com.android.builder.dexing.DexingType;
import com.android.utils.FileUtils;

/**
 * Bind resources for library bundle - FOR FUTURE USE ONLY
 */
public class BindResourcesOp implements BuildOp<PreCompilerContext> {

	public BindResourcesOp() {
	}
	
	@Override
	public boolean execute(PreCompilerContext context) throws CoreException, AbortBuildException, InterruptedException {
		IProject project = context.getProject();
        if (context.isDebugLog()) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "%s packaging resources", project.getName());
        }
        createAppProcessTask(context.getVariantContext());
        // Prepare for commit by deleting old linked resources file from project. Does not delete file on file system.
        IFolder projectFolder = BaseProjectHelper.getAndroidOutputFolder(context.getProject());
        if (projectFolder.exists(new org.eclipse.core.runtime.Path(AndmoreAndroidConstants.FN_RESOURCES_AP_))) {
        	IFile toDelete = projectFolder.getFile(AndmoreAndroidConstants.FN_RESOURCES_AP_);
        	toDelete.delete(true, null);
        }
        TaskFactory taskFactory = context.getTaskFactory();
    	synchronized(taskFactory) {
    		taskFactory.wait();
    	}
		return true;
	}

	@Override
	public void commit(PreCompilerContext context) throws IOException {
		File genDirectory = context.getGenFolder().getLocation().toFile();
        File linkedResourcesFile = linkedResourcesSourceFile(context);
        File rClassSourceOutputDir = rClassSourceOutputDir(context);
		// Copy generated R.java files to project
        FileUtils.copyDirectoryContentToDirectory(rClassSourceOutputDir, genDirectory);
	   	IFolder projectFolder = BaseProjectHelper.getAndroidOutputFolder(context.getProject());
		File destinationDir = projectFolder.getLocation().toFile();
		File oldFile = new File(destinationDir, SdkConstants.FN_RESOURCE_TEXT);
		if (oldFile.exists())
			oldFile.delete();
		else if (!destinationDir.exists() && !destinationDir.mkdirs())
			throw new IOException("Failed to create path " + destinationDir.toString());
		File newFile = new File(symbolsSourceLocation(context), SdkConstants.FN_RESOURCE_TEXT);
		FileUtils.copyFileToDirectory(newFile, destinationDir);
        File oldLinkedResourcesFile = new File(destinationDir, AndmoreAndroidConstants.FN_RESOURCES_AP_);
		if (oldLinkedResourcesFile.exists())
			oldLinkedResourcesFile.delete();
		FileUtils.copyFile(linkedResourcesFile, oldLinkedResourcesFile);
        // we've at least attempted to run aapt, save the fact that we don't have to
        // run it again, unless there's a new resource change.
        context.saveMustCompileResources(false);
	}

	@Override
	public String getDescription() {
		return "Bind resources";
	}

	private File rClassSourceOutputDir(PreCompilerContext context) {
		return context.getVariantContext().getRClassSourceOutputDir();
	}
	
	private File linkedResourcesSourceFile(PreCompilerContext context) {
        return context.getVariantContext().getProcessResourcePackageOutputFile();
	}
	
	private File symbolsSourceLocation(PreCompilerContext context) {
		return context.getVariantContext().getSymbolsOutputDir();
	}

	private void createAppProcessTask(VariantContext variantScope) {
		boolean useAaptToGenerateLegacyMultidexMainDexProguardRules = 
		    variantScope.getDexingType() == DexingType.LEGACY_MULTIDEX;
		String baseName = variantScope.getAndworxProject().getName();
		NonNamespacedLinkResourcesTask linkTask = 
				 AndworxFactory.instance().getNonNamespacedLinkResourcesTask(variantScope);
		linkTask.configure(
				 useAaptToGenerateLegacyMultidexMainDexProguardRules, 
				 MergeType.MERGE.getOutputType(),
				 baseName,
				 false);
		linkTask.schedule();
	}
}
