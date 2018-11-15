/*
 * Copyright (C) 2012 The Android Open Source Project
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
package org.eclipse.andworx.build.task;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;

import org.eclipse.andworx.api.attributes.AndroidArtifacts.ArtifactType;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;

import com.android.annotations.NonNull;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.compiler.DirectoryWalker;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Compile Renderscript files
 */
public class RenderscriptCompileTask extends StandardBuildTask {
	
	public static final String TASK_NAME = "renderscript compile";
    private static ILogger logger = SdkLogger.getLogger(RenderscriptCompileTask.class.getName());

    private final VariantContext variantScope;
    private final BuildHelper buildHelper;
    private final AndroidBuilder androidBuilder;

	public RenderscriptCompileTask(VariantContext variantScope, BuildHelper buildHelper, AndroidBuilder androidBuilder, TaskFactory taskFactory) {
		super(taskFactory);
		this.variantScope = variantScope;
		this.buildHelper = buildHelper;
		this.androidBuilder = androidBuilder;
	}

	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	@Override
	public Future<Void> doFullTaskAction() {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
            AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        	compileAllRenderscriptFiles(variantConfig);
    	    actualResult.set(null);
        } catch (Exception e) {
    	   actualResult.setException(e);
        }
	    return actualResult;
    }

	private void compileAllRenderscriptFiles(AndworxVariantConfiguration variantConfig) throws IOException, InterruptedException, ProcessException {
		Collection<File> sourceDirectories = variantConfig.getRenderscriptSourceList();
		File sourceDestDir = variantScope.getRenderscriptSourceOutputDir();
		buildHelper.prepareDir(sourceDestDir);
		File resDestDir = variantScope.getRenderscriptResOutputDir();
		buildHelper.prepareDir(resDestDir);
		File objDestDir = variantScope.getRenderscriptObjOutputDir();
		buildHelper.prepareDir(objDestDir);
		File libDestDir = variantScope.getRenderscriptLibOutputDir();
		buildHelper.prepareDir(libDestDir);
		androidBuilder.compileAllRenderscriptFiles(
                sourceDirectories,
                getImportFolders(sourceDirectories),
                sourceDestDir,
                resDestDir,
                objDestDir,
                libDestDir,
                variantConfig.getRenderscriptTarget(),
                variantConfig.getBuildType().isRenderscriptDebuggable(),
                variantConfig.getBuildType().getRenderscriptOptimLevel(),
                variantConfig.getRenderscriptNdkModeEnabled(),
                variantConfig.getRenderscriptSupportModeEnabled(),
                // TODO - NDK config
                null, //getNdkConfig() == null ? null : getNdkConfig().getAbiFilters(),
                new LoggedProcessOutputHandler(logger));

	}
	
    // Returns the import folders. If the .rsh files are not directly under the import folders,
    // we need to get the leaf folders, as this is what llvm-rs-cc expects.
    @NonNull
    private Collection<File> getImportFolders(Collection<File> sourceDirectories) throws IOException {
        Set<File> results = Sets.newHashSet();

        Collection<File> dirs = Lists.newArrayList();
    	Collection<File> importDirs = variantScope.getArtifactFileCollection(ArtifactType.RENDERSCRIPT);
        dirs.addAll(importDirs);
        dirs.addAll(sourceDirectories);

        for (File dir : dirs) {
            // TODO(samwho): should "rsh" be a constant somewhere?
            DirectoryWalker.builder()
                    .root(dir.toPath())
                    .extensions("rsh")
                    .action((start, path) -> results.add(path.getParent().toFile()))
                    .build()
                    .walk();
        }

        return results;
    }
}
