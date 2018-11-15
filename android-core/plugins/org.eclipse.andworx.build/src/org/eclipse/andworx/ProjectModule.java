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

import java.io.File;

import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.ProjectProfile;

import dagger.Module;
import dagger.Provides;

/**
 * Reads project profile from database given project name and location
 */
@Module
public class ProjectModule {

	private final String projectName;
	private final File projectLocation;
	
	public ProjectModule(String projectName, File projectLocation) {
		this.projectName = projectName;
		this.projectLocation = projectLocation;
	}
	
    @Provides
	ProjectProfile provideProjectProfile(AndroidConfiguration androidConfig) {
    	ProjectProfile profile = null;
		try {
			profile = androidConfig.getProfile(projectName, projectLocation);
		} catch (InterruptedException e) {
			Thread.interrupted();
			throw new AndworxException(projectName + " interrupted");
		}
		return profile;
	}
}
