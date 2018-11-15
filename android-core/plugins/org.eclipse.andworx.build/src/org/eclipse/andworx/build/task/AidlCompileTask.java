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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import org.eclipse.andworx.api.attributes.AndroidArtifacts.ArtifactType;
import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;

import com.android.annotations.NonNull;
import com.android.builder.compiling.DependencyFileProcessor;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.internal.incremental.DependencyData;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Compile aidl files
 */
public class AidlCompileTask extends StandardBuildTask {
	
	public static final String TASK_NAME = "aidl compile";

    private static class DepFileProcessor implements DependencyFileProcessor {
        List<DependencyData> dependencyDataList =
                Collections.synchronizedList(Lists.newArrayList());

        List<DependencyData> getDependencyDataList() {
            return dependencyDataList;
        }

        @Override
        public DependencyData processFile(@NonNull File dependencyFile) throws IOException {
            DependencyData data = DependencyData.parseDependencyFile(dependencyFile);
            if (data != null) {
                dependencyDataList.add(data);
            }

            return data;
        }
    }

    private static ILogger logger = SdkLogger.getLogger(AidlCompileTask.class.getName());
    
    private final VariantContext variantScope;
    private final BuildHelper buildHelper;
    private final AndroidBuilder androidBuilder;

	public AidlCompileTask(VariantContext variantScope, BuildHelper buildHelper, AndroidBuilder androidBuilder, TaskFactory taskFactory) {
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
        AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        File sourceOutputDir = variantScope.getAidlSourceOutputDir();
    	File packagedAidlDir = null;
    	Collection<String> packageWhitelist = null;
    	Collection<File> sourceDirs = variantConfig.getAidlSourceList();
    	Collection<File> importDirs = variantScope.getArtifactFileCollection(ArtifactType.AIDL);
        if (variantConfig.getType() == VariantType.LIBRARY) {
        	packagedAidlDir = variantScope.getPackagedAidlDir();
            variantScope.addOutput(
                    OutputType.AIDL_PARCELABLE,
                    Collections.singletonList(packagedAidlDir),
                    TASK_NAME);
            packageWhitelist = Collections.emptyList();
            		// TODO aidl package white list
            		//variantScope.getAidlPackageWhiteList();
        }
        try {
        	buildHelper.prepareDir(sourceOutputDir);
        	if (packagedAidlDir != null)
        		buildHelper.prepareDir(packagedAidlDir);
            compileAllFiles(
            		sourceDirs,
            		importDirs,
            		sourceOutputDir, 
            		packagedAidlDir, 
            		packageWhitelist, 
            		new DepFileProcessor());
        	actualResult.set(null);
        } catch (Exception e) {
        	actualResult.setException(e);
        }
		return actualResult;
	}

   /**
     * Action methods to compile all the files.
     *
     * <p>The method receives a {@link DependencyFileProcessor} to be used by the {@link
     * com.android.builder.internal.compiler.SourceSearcher.SourceFileProcessor} during the
     * compilation.
     *
     * @param dependencyFileProcessor a DependencyFileProcessor
     */
    private void compileAllFiles(
    		Collection<File> sourceDirs,
    		Collection<File> importDirs,
    		File sourceOutputDir,
    		File packagedAidlDir,
    		Collection<String> packageWhitelist,
     		DependencyFileProcessor dependencyFileProcessor)
            throws InterruptedException, ProcessException, IOException {
	   	androidBuilder.compileAllAidlFiles(
	                sourceDirs,
	                sourceOutputDir,
	                packagedAidlDir,
	                packageWhitelist,
	                importDirs,
	                dependencyFileProcessor,
	                new LoggedProcessOutputHandler(logger));
    }
}
