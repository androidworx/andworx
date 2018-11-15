/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.project;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.andworx.config.AndroidConfig;
import org.eclipse.andworx.model.ProjectSourceProvider;
import org.eclipse.andworx.model.SourceSet;

import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavor;

/**
 * Android project configuration extracted from database
 * This provides information need to create AndworxProject and VariantContext objects
 */
public class ProjectConfiguration {

    private final String name;
    private final ProjectProfile profile;
    private final File projectDirectory;
    private final Collection<BuildTypeContainer> buildTypes;
    private final AndroidConfig androidConfig;
    private final Map<String, ProductFlavor> productFlavorMap;
	private final Map<String, ProjectSourceProvider> sourceSetMap;

	/**
	 * Construct ProjectConfiguration object
	 * @param name
	 * @param profile
	 * @param projectDirectory
	 * @param androidConfig
	 * @param productFlavors
	 * @param buildTypes
	 * @param sourceSets
	 */
	public ProjectConfiguration(String name, 
			ProjectProfile profile,
    		File projectDirectory, 
    		AndroidConfig androidConfig, 
    		Collection<ProductFlavor> productFlavors,
    		Collection<BuildTypeContainer> buildTypes,
    		Collection<ProjectSourceProvider> sourceSets) {
        this.name = name;
        this.profile = profile;
   	    this.projectDirectory = projectDirectory;
   	    this.androidConfig = androidConfig;
        this.buildTypes = buildTypes;
   	    productFlavorMap = new HashMap<>();
   	    for (ProductFlavor productFlavor: productFlavors)
   	   	    productFlavorMap.put(productFlavor.getName(), productFlavor);
   	    sourceSetMap = new HashMap<>();
   	    for (ProjectSourceProvider sourceProvider: sourceSets)
   	    	sourceSetMap.put(sourceProvider.getName(), sourceProvider);
	}
	
	public ProjectSourceProvider getDefaultSourceSet() {
		return sourceSetMap.get(SourceSet.MAIN_SOURCE_SET_NAME);
	}

	public String getName() {
		return name;
	}

	public ProjectProfile getProfile() {
		return profile;
	}

	public File getProjectDirectory() {
		return projectDirectory;
	}

	public Collection<BuildTypeContainer> getBuildTypes() {
		return buildTypes;
	}

	public AndroidConfig getAndroidConfig() {
		return androidConfig;
	}

	public Map<String, ProductFlavor> getProductFlavorMap() {
		return productFlavorMap;
	}

	public Map<String, ProjectSourceProvider> getSourceSetMap() {
		return sourceSetMap;
	}
}
