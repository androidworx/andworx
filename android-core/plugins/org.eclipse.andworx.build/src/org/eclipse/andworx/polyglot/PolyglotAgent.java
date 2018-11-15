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
package org.eclipse.andworx.polyglot;

import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.project.Identity;
import org.eclipse.andworx.repo.DependencyArtifact;
import org.eclipse.core.resources.IProject;

/**
 * Agent which parses configuration files in supported scripting languages
 */
public class PolyglotAgent {
    /** Placeholder identity to be replaced when details available from imported project */
    public static final Dependency NEW_IDENTITY = new DependencyArtifact("", "", "");
	public static final String DEFAULT_GROUP_ID = "android";
	public static final String DEFAULT_VERSION = "1.0.0-SNAPHOT";
 
	/**
	 * Returns default identity for specified project
	 * @param iProject
	 * @return Dependency object
	 */
	public Identity getProjectIdentity(IProject iProject) {
		return new Identity(DEFAULT_GROUP_ID, iProject.getName(), DEFAULT_VERSION);
	}

}
