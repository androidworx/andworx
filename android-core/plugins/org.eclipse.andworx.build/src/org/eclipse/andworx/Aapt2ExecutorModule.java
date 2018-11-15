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
package org.eclipse.andworx;

import org.eclipse.andworx.aapt.Aapt2Executor;
import org.eclipse.andworx.context.VariantContext;

import com.android.sdklib.BuildToolInfo;

import dagger.Module;
import dagger.Provides;

@Module
public class Aapt2ExecutorModule {

	private final VariantContext variantScope;

	public Aapt2ExecutorModule(VariantContext variantScope) {
		this.variantScope = variantScope;
	}

	@Provides
	Aapt2Executor providesAapt2Executor() {
        String buildToolsVersion = variantScope.getAndworxProject().getBuildToolsVersion();
		BuildToolInfo buildToolInfo = variantScope.getBuildToolsInfo(buildToolsVersion);
		Aapt2Executor.Builder builder = new Aapt2Executor.Builder(buildToolInfo);
		return builder.build();
	}
}
