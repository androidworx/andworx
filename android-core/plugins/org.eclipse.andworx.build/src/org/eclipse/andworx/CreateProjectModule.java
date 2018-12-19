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

import org.eclipse.andworx.entity.ProjectBean;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.AndroidDigest;
import org.eclipse.andworx.project.ProjectProfile;

import dagger.Module;
import dagger.Provides;

/**
 * Constructs project profile given project name, profile and AndroidConfigurationBuilder object
 */
@Module
public class CreateProjectModule {

	private final String projectName;
	private final ProjectProfile projectProfile;
	private final AndroidDigest androidDigest;

	public CreateProjectModule(String projectName, 
			ProjectProfile projectProfile, 
			AndroidDigest androidDigest) {
		this.projectName = projectName;
		this.projectProfile = projectProfile;
		this.androidDigest = androidDigest;
	}

    @Provides
	ProjectProfile provideProjectProfile(AndroidConfiguration androidConfig) {
    	// Returns profile updated with new project ID
		try {
		    ProjectBean projectBean = androidConfig.createProject(projectName, projectProfile);
		    androidConfig.persist(projectBean, androidDigest);
		} catch (InterruptedException e) {
			Thread.interrupted();
			throw new AndworxException(projectName + " interrupted");
		}
		return projectProfile;
   }}
