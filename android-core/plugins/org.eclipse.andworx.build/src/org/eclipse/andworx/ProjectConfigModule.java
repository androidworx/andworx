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

import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.ProjectConfiguration;
import org.eclipse.andworx.project.ProjectProfile;

import dagger.Module;
import dagger.Provides;

/**
 * Constructs project configuration given profile, project name and location
 */
@Module
public class ProjectConfigModule {

	private final ProjectProfile profile;
	private final String projectName;
	private final File projectLocation;
	private final AndroidEnvironment androidEnvironment;

	public ProjectConfigModule(ProjectProfile profile, String projectName, File projectLocation, AndroidEnvironment androidEnvironment) {
		this.profile = profile;
		this.projectName = projectName;
		this.projectLocation = projectLocation;
		this.androidEnvironment = androidEnvironment;
	}
	
	@Provides
	ProjectConfiguration provideProjectConfiguration(AndroidConfiguration androidConfig) {
		try {
			return androidConfig.getProjectConfiguration(
					profile, 
					projectName, 
					projectLocation,
					androidEnvironment);
		} catch (InterruptedException e) {
			Thread.interrupted();
			throw new AndworxException(projectName + " interrupted");
		}
	}
	   
}
