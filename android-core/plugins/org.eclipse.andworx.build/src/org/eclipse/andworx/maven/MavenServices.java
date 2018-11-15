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
package org.eclipse.andworx.maven;

import java.io.File;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.andworx.repo.ProjectRepository;

/**
 * Service interface for m2e interactions 
 * @see org.eclipse.m2e.core.MavenPlugin
 */
public interface MavenServices {
	/**
	 * Returns POM file name with "xml" extension as specified by m2e library
	 * @return POM file name
	 */
	String getPomFilename();
	/**
	 * Create a Maven project wrapper for configuration and resolution of aar and jar dependencies
	 * @param mavenProject Maven project which provides runtime values based on a POM
	 * @return AndworxMavenProject object
	 */
    AndworxMavenProject createAndworxProject(MavenProject mavenProject);
    /**
     * Create a POM file from a given model 
     * @param pomFile The file to create. If one already exists, it will be deleted
     * @param model Project model
     */
    void createMavenModel(File pomFile, Model model);
    /**
     * Build and return a Maven project object from a POM file
     * @param pomXml POM
     * @return MavenProject object
     */
	MavenProject readMavenProject(File pomXml);
	/**
	 * Configure dependency AARs. For each archive, an expanded copy is created and stored in a dedicated Maven repository.
	 * @param mavenProject A Maven project wrapper for configuration and resolution of aar and jar dependencies
	 * @param repositoryLocation Location of the library projects repository
	 * @return ProjectRepository object
	 */
	ProjectRepository configureLibraries(
			AndworxMavenProject mavenProject, 
			File repositoryLocation);
}
