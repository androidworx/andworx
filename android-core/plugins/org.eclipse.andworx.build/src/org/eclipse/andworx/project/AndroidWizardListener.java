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

import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;

import com.android.ide.common.xml.ManifestData;

/**
 *  Interface to project import wizard page
 */
public interface AndroidWizardListener {
	/**
	 * Handle Android manifest file parsed
	 * @param manifestData Manifest data
	 */
	void onManifestParsed(ManifestData manifestData);
	/**
	 * Handle configuration file parsed
	 * @param androidConfigurationBuilder Assembles configuration content extracted by a Groovy AST parser into JPA entity beans and then persists them
	 */
	void onConfigParsed(AndroidConfigurationBuilder androidConfigurationBuilder);
	/**
	 * Handle profile resolved
	 * @param projectProfile Project profile
	 * @param isResolved Flag set true if profile is resolved
	 * @param message Message to display
	 */
	void onProfileResolved(
			ProjectProfile projectProfile, 
			boolean isResolved, 
			String message);
	/**
	 * Display error
	 * @param message Error mesage
	 */
	void onError(String message);
}
