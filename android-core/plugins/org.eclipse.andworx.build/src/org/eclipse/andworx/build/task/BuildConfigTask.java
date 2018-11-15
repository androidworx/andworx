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
package org.eclipse.andworx.build.task;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.core.AndworxVariantConfiguration;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.runtime.CoreException;

import com.android.builder.compiling.BuildConfigGenerator;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.SettableFuture;

/**
 * BuildConfig.java generated
 */
public class BuildConfigTask extends StandardBuildTask {

	public static final String TASK_NAME = "Generate " + BuildConfigGenerator.BUILD_CONFIG_NAME;
    //private static SdkLogger logger = SdkLogger.getLogger(BuildConfigTask.class.getName());
 
    private final String manifestPackage;
    private final VariantContext variantScope;
    
	/** Source file on commit */
	private File newFile;

	public BuildConfigTask(String manifestPackage, VariantContext variantScope, TaskFactory taskFactory) {
		super(taskFactory);
		this.manifestPackage = manifestPackage;
		this.variantScope = variantScope;
	}

	public File getNewFile() {
		return newFile;
		
	}

	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	@Override
	public Future<Void> doFullTaskAction() {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
            doBuildConfig();
        	actualResult.set(null);
        } catch (Exception e) {
        	actualResult.setException(e);
        }
		return actualResult;
	}

    private void doBuildConfig()
            throws IOException, CoreException {
        AndworxVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
        // Clear the folder in case the packagename changed.
        File destinationDir = variantScope.getBuildConfigSourceOutputDir();
        BuildConfigGenerator generator = new BuildConfigGenerator(
        		destinationDir, 
        		manifestPackage);
        // For commit
        newFile = generator.getBuildConfigFile();
        boolean debugMode = variantConfig.getBuildType().isDebuggable();
        // We want to avoid reporting "condition is always true"
        // from the data flow inspection, so use a non-constant value. However, that defeats
        // the purpose of this flag (when not in debug mode, if (BuildConfig.DEBUG && ...) will
        // be completely removed by the compiler), so as a hack we do it only for the case
        // where debug is true, which is the most likely scenario while the user is looking
        // at source code.
        generator
                .addField(
                        "boolean",
                        "DEBUG",
                        debugMode ? "Boolean.parseBoolean(\"true\")" : "false")
                .addField("String", "APPLICATION_ID", '"' + variantConfig.getApplicationId() + '"')
                .addField("String", "BUILD_TYPE", '"' + variantConfig.getBuildType().getName() + '"')
                .addField("int", "VERSION_CODE", Integer.toString(variantConfig.getVersionCode()))
                .addField(
                        "String", "VERSION_NAME", '"' + Strings.nullToEmpty(variantConfig.getVersionName()) + '"')
                .addItems(variantConfig.getBuildConfigItems());
        generator.generate();
    }
}
