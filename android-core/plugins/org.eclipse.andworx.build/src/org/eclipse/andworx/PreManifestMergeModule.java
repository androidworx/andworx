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
package org.eclipse.andworx;

import java.io.File;

import org.eclipse.andworx.build.task.PreManifestMergeTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.task.TaskFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class PreManifestMergeModule {

	private final VariantContext variantScope;
	private final File manifestOutputDir;

	public PreManifestMergeModule(VariantContext variantScope, File manifestOutputDir) {
		this.variantScope = variantScope;
		this.manifestOutputDir = manifestOutputDir;
	}
	
	@Provides
	PreManifestMergeTask providePreManifestMergeTask(
			BuildHelper buildHelper, 
			BuildElementFactory buildElementFactory, 
			TaskFactory taskFactory) {
		return new PreManifestMergeTask(variantScope, manifestOutputDir, buildHelper, buildElementFactory, taskFactory);
	}
}
