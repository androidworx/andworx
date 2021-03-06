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

import org.eclipse.andworx.build.task.AidlCompileTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.task.TaskFactory;

import com.android.builder.core.AndroidBuilder;

import dagger.Module;
import dagger.Provides;

@Module
public class AidlCompileModule {

    private final VariantContext variantScope;

	public AidlCompileModule(VariantContext variantScope) {
		this.variantScope = variantScope;
	}
	
	@Provides
	AidlCompileTask provideAidlCompileTask(BuildHelper buildHelper, AndroidBuilder androidBuilder, TaskFactory taskFactory) {
		return new AidlCompileTask(variantScope, buildHelper, androidBuilder, taskFactory);
	}
}
