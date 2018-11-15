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
import java.util.Collections;
import java.util.concurrent.Future;

import org.eclipse.andworx.build.OutputType;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.task.StandardBuildTask;
import org.eclipse.andworx.task.TaskFactory;

import com.android.SdkConstants;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Prepare manifest merge output files
 */
public class PreManifestMergeTask extends StandardBuildTask {

	private final VariantContext variantScope;
	private final File manifestOutputDir;
	private final BuildHelper buildHelper;
	private final BuildElementFactory buildElementFactory;
	
	public PreManifestMergeTask(
			VariantContext variantScope,
			File manifestOutputDir,
			BuildHelper buildHelper, 
			BuildElementFactory buildElementFactory, 
			TaskFactory taskFactory) {
		super(taskFactory);
		this.variantScope = variantScope;
		this.manifestOutputDir = manifestOutputDir;
		this.buildHelper = buildHelper;
		this.buildElementFactory = buildElementFactory;
	}
	
	@Override
	public String getTaskName() {
		return "Prepare manifest";
	}

	@Override
	public Future<Void> doFullTaskAction() {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
        	// Write output metadata file and register in variant context
     		buildHelper.prepareDir(manifestOutputDir);
    		File newFile = new File(manifestOutputDir, SdkConstants.FN_ANDROID_MANIFEST_XML);
    		buildHelper.writeOutput(OutputType.MERGED_MANIFESTS, newFile, variantScope.getMainSplit());
     		variantScope.addOutput(
     				OutputType.MERGED_MANIFESTS, 
     				Collections.singletonList(manifestOutputDir), 
     				getTaskName());
            variantScope.addOutput(
            		OutputType.MANIFEST_METADATA,
	    	        Collections.singletonList(
	    			    buildElementFactory.getMetadataFile(variantScope.getManifestOutputDirectory())),
	    	        getTaskName());
            actualResult.set(null);
        } catch (Exception e) {
        	actualResult.setException(e);
        }
		return actualResult;
	}
}
