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

import org.eclipse.andworx.build.task.ManifestMergerTask;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.task.ManifestMergeHandler;
import org.eclipse.andworx.task.TaskFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class ManifestMergerModule {

	private final ManifestMergeHandler manifestMergeHandler;

	public ManifestMergerModule(ManifestMergeHandler manifestMergeHandler) {
		this.manifestMergeHandler = manifestMergeHandler;
	}
	
	@Provides
	ManifestMergerTask provideManifestMergerTask(BuildHelper buildHelper, TaskFactory taskFactory) {
		return new ManifestMergerTask(manifestMergeHandler, buildHelper, taskFactory);
	}
}
