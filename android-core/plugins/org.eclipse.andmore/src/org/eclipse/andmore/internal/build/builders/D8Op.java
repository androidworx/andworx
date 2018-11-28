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

import static com.android.SdkConstants.FN_APK_CLASSES_DEX;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.build.builders.BaseBuilder.AbortBuildException;
import org.eclipse.andmore.internal.project.BaseProjectHelper;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.task.D8Task;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.Transform;
import org.eclipse.andworx.transform.TransformAgent;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.utils.FileUtils;

/**
 * Dex operation to transforms java bytecode to Android machine code in 2 stages using D8.
 * Stage 1 builds archives. Stage 2 merges the archives.
 *
 */
public class D8Op extends TransformAgent implements BuildOp<PostCompilerContext>, Transform {
	
	public D8Op() {
	}

	@Override
	public String getName() {
		return ProjectBuilder.D8;
	}

	@Override
	public boolean execute(PostCompilerContext context) throws CoreException, AbortBuildException, InterruptedException {
		IProject project = context.getProject();
        if (context.isDebugLog()) {
            AndmoreAndroidPlugin.log(IStatus.INFO, "%s %s", project.getName(), getName());
        }
        VariantContext variantScope = context.getVariantContext();
        File[] outputDirs = new File[] { getOutputRootDir("dexBuilder", variantScope),  getDexMergeDir(variantScope)};
        AndworxContext objectFactory = AndworxFactory.instance();
        D8Task d8Task = objectFactory.getD8Task(context, variantScope);
        ProjectState projectState = objectFactory.getProjectState(project);
        d8Task.configure (
        		outputDirs,
        		projectState.getBootClasspath(true),
        		this,
       	    	projectState.getMessageReceiver(),
        		variantScope.getVariantConfiguration().getBuildType().isDebuggable());
        d8Task.schedule();
        TaskFactory taskFactory = context.getTaskFactory();
    	synchronized(taskFactory) {
    		taskFactory.wait();
    	}
		return true;
	}

	@Override
	public void commit(PostCompilerContext context) throws IOException {
	   	IFolder projectFolder = BaseProjectHelper.getAndroidOutputFolder(context.getProject());
		File destinationDir = projectFolder.getLocation().toFile();
        File oldDexFile = new File(destinationDir, FN_APK_CLASSES_DEX);
		if (oldDexFile.exists())
			oldDexFile.delete();
		File newDexFile = FileUtils.join(getDexMergeDir(context.getVariantContext()), "0", FN_APK_CLASSES_DEX);
		FileUtils.copyFile(newDexFile, oldDexFile);
	    context.saveConvertToDex(false);
	}

	@Override
	public String getDescription() {
		return D8Task.TASK_NAME;
	}

	@Override
	public Set<? super Scope> getReferencedScopes() {
		return Collections.emptySet();
	}

	@Override
	public Set<ContentType> getInputTypes() {
		return Collections.emptySet();
	}

	private File getDexMergeDir(VariantContext variantScope) {
		return getOutputRootDir("dexMerger", variantScope);
	}

}
