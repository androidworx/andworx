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
package org.eclipse.andworx.project;

import org.apache.maven.model.Model;
import org.eclipse.andworx.config.AndroidConfig;
import org.eclipse.andworx.entity.ProjectBean;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.model.RepositoryUrl;

import com.android.builder.model.SigningConfig;

/**
 * Configuration content extracted from a build file
 */
public interface AndroidDigest {
	/**
	 * Returns user configuration settings within the "android" block, excluding those shared by BuildType
	 * @return AndroidConfig object
	 */
	public AndroidConfig getAndroidConfig();

	/**
	 * Returns Maven model containing project dependendencies
	 * @return Model object
	 */
	public Model getMavenModel();

	/**
	 * Returns debug Signing Configuration
	 * @return SigningConfig object
	 */
	public SigningConfig getDefaultSigningConfig();

	/**
	 * Returns source path for given source set bype
	 * @param codeSource Source set type
	 * @return path
	 */
	public String getSourceFolder(CodeSource codeSource);

	/**
	 * Returns array containing entity objects ready to be persisted
	 * @param projectBean Associated Project entity bean
	 * @return Object array
	 */
	public Object[] asEntities(ProjectBean projectBean);

	/**
	 * Add RepositoryUrl
	 * @param repositoryUrl Repository Url consisting of name and url
	 */
	public void addRepositoryUrl(RepositoryUrl repositoryUrl);
}
