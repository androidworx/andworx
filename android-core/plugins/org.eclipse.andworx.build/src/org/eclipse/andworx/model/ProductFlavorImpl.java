/*
 * Copyright (C) 2012 The Android Open Source Project
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
package org.eclipse.andworx.model;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import com.android.annotations.NonNull;
import com.android.builder.core.DefaultVectorDrawablesOptions;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.VectorDrawablesOptions;

/**
 * Implements Android SDK ProductFlavor model
 */
public class ProductFlavorImpl implements ProductFlavor {

	/**  Encapsulates all product flavor configurations for a project, excluding those shared by BuildType */
	private final ProductFlavorAtom poductFlavorAtom;
	/** Base config object for Build Type and Product flavor */
	private final BaseConfig baseConfig;
	/** Vector Drawables Options TODO - configure */
    @NonNull
    private DefaultVectorDrawablesOptions vectorDrawablesOptions;
	
	public ProductFlavorImpl(ProductFlavorAtom poductFlavorAtom, BaseConfig baseConfig) {
		this.poductFlavorAtom = poductFlavorAtom;
		this.baseConfig = baseConfig;
        vectorDrawablesOptions = new DefaultVectorDrawablesOptions();
	}
	
	@Override
	public String getApplicationIdSuffix() {
		return baseConfig.getApplicationIdSuffix();
	}

	@Override
	public String getVersionNameSuffix() {
		return baseConfig.getVersionNameSuffix();
	}

	@Override
	public Map<String, ClassField> getBuildConfigFields() {
		return baseConfig.getBuildConfigFields();
	}

	@Override
	public Map<String, ClassField> getResValues() {
		return baseConfig.getResValues();
	}

	@Override
	public Collection<File> getProguardFiles() {
		return baseConfig.getProguardFiles();
	}

	@Override
	public Collection<File> getConsumerProguardFiles() {
		return baseConfig.getConsumerProguardFiles();
	}

	@Override
	public Collection<File> getTestProguardFiles() {
		return baseConfig.getTestProguardFiles();
	}

	@Override
	public Map<String, Object> getManifestPlaceholders() {
		return baseConfig.getManifestPlaceholders();
	}

	@Override
	public Boolean getMultiDexEnabled() {
		return baseConfig.getMultiDexEnabled();
	}

	@Override
	public File getMultiDexKeepFile() {
		return baseConfig.getMultiDexKeepFile();
	}

	@Override
	public File getMultiDexKeepProguard() {
		return baseConfig.getMultiDexKeepProguard();
	}

	@Override // Dimensions not supported
	public String getDimension() {
		return null;
	}

	@Override
	public String getName() {
		return poductFlavorAtom.getName();
	}

	@Override
	public String getApplicationId() {
		return poductFlavorAtom.getApplicationId();
	}

	@Override
	public Integer getVersionCode() {
		return poductFlavorAtom.getVersionCode();
	}

	@Override
	public String getVersionName() {
		return poductFlavorAtom.getVersionName();
	}

	@Override
	public ApiVersion getMinSdkVersion() {
		return poductFlavorAtom.getMinSdkVersion();
	}

	@Override
	public ApiVersion getTargetSdkVersion() {
		return poductFlavorAtom.getTargetSdkVersion();
	}

	@Override
	public Integer getMaxSdkVersion() {
		return poductFlavorAtom.getMaxSdkVersion();
	}

	@Override
	public Integer getRenderscriptTargetApi() {
		return poductFlavorAtom.getRenderscriptTargetApi();
	}

	@Override
	public Boolean getRenderscriptSupportModeEnabled() {
		return poductFlavorAtom.getRenderscriptSupportModeEnabled();
	}

	@Override
	public Boolean getRenderscriptSupportModeBlasEnabled() {
		return poductFlavorAtom.getRenderscriptSupportModeBlasEnabled();
	}

	@Override
	public Boolean getRenderscriptNdkModeEnabled() {
		return poductFlavorAtom.getRenderscriptNdkModeEnabled();
	}

	@Override
	public String getTestApplicationId() {
		return poductFlavorAtom.getApplicationId();
	}

	@Override
	public String getTestInstrumentationRunner() {
		return poductFlavorAtom.getTestInstrumentationRunner();
	}

	@Override
	public Map<String, String> getTestInstrumentationRunnerArguments() {
		return poductFlavorAtom.getTestInstrumentationRunnerArguments();
	}

	@Override
	public Boolean getTestHandleProfiling() {
		return poductFlavorAtom.getTestHandleProfiling();
	}

	@Override
	public Boolean getTestFunctionalTest() {
		return poductFlavorAtom.getTestFunctionalTest();
	}

	@Override
	public Collection<String> getResourceConfigurations() {
		return poductFlavorAtom.getResourceConfigurations();
	}

	@Override
	public SigningConfig getSigningConfig() {
		return poductFlavorAtom.getSigningConfig();
	}

	@Override
	public VectorDrawablesOptions getVectorDrawables() {
		return vectorDrawablesOptions;
		//return poductFlavorAtom.getVectorDrawables();
	}

	@Override
	public Boolean getWearAppUnbundled() {
		return poductFlavorAtom.getWearAppUnbundled();
	}

}
