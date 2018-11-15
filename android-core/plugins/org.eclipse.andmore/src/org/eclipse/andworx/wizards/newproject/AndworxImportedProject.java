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
package org.eclipse.andworx.wizards.newproject;

import java.io.File;

import org.eclipse.andmore.internal.wizards.newproject.ImportedProject;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.project.ProjectProfile;

import com.android.ide.common.xml.ManifestData;

/**
 * Information required to import an Andworx project
 */
public class AndworxImportedProject implements ImportedProject {

	private String projectName;
	private ProjectProfile projectProfile;
	private File location;
	private ManifestData manifestData;
	private AndroidConfigurationBuilder androidConfigurationBuilder;

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public void setProjectProfile(ProjectProfile projectProfile) {
		this.projectProfile = projectProfile;
	}

	public void setLocation(File location) {
		this.location = location;
	}

	public void setManifestData(ManifestData manifestData) {
		this.manifestData = manifestData;
	}

	public void setAndroidConfigBuilder(AndroidConfigurationBuilder androidConfigurationBuilder) {
		this.androidConfigurationBuilder = androidConfigurationBuilder;
	}

	@Override
	public String getProjectName() {
		return projectName;
	}

	@Override
	public File getLocation() {
		return location;
	}

	@Override
	public ProjectProfile getProjectProfile() {
		return projectProfile;
	}

	@Override
	public String getRelativePath() {
		return "";
	}

	@Override
	public ManifestData getManifest() {
		return manifestData;
	}

	@Override
	public String getSourceFolder() {
		return androidConfigurationBuilder.getSourceFolder(CodeSource.javaSource);
	}

	@Override
	public AndroidConfigurationBuilder getAndroidConfig() {
		return androidConfigurationBuilder;
	}
}
