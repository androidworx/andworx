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
package org.eclipse.andworx.model;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import com.android.annotations.Nullable;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.BuildType;
import com.android.builder.model.ClassField;
import com.android.builder.model.SigningConfig;

/**
 * Implements Android SDK BuildType model plus Gradle extras
 */
public class BuildTypeImpl implements BuildType {

	/** Encapsulates all build type configurations for a project, excluding those shared by ProductFlavor */
	private final BuildTypeAtom buildTypeAtom;
	/** Base config object for Build Type and Product flavor */
	private final BaseConfig baseConfig;

	/**
	 * Construct BuildTypeImpl object
	 * @param buildTypeAtom Encapsulates all build type configurations for a project, excluding those shared by ProductFlavor
	 * @param baseConfig Base config object for Build Type and Product flavor
	 */
	public BuildTypeImpl(BuildTypeAtom buildTypeAtom, BaseConfig baseConfig) {
		this.buildTypeAtom = buildTypeAtom;
		this.baseConfig = baseConfig;
	}
	
    /**
     * Whether to crunch PNGs.
     *
     * <p>Setting this property to <code>true</code> reduces of PNG resources that are not already
     * optimally compressed. However, this process increases build times.
     *
     * <p>PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     */
    @Nullable
    public Boolean isCrunchPngs() {
    	return buildTypeAtom.isCrunchPngs();
    }

    /**
     * Flag set true if shrink resources enabled
     * @return boolean
     */
	public boolean isShrinkResources() {
		return buildTypeAtom.isShrinkResources();
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

	@Override
	public String getName() {
		return buildTypeAtom.getName();
	}

	@Override
	public boolean isDebuggable() {
		return buildTypeAtom.isDebuggable();
	}

	@Override
	public boolean isTestCoverageEnabled() {
		return buildTypeAtom.isTestCoverageEnabled();
	}

	@Override
	public boolean isPseudoLocalesEnabled() {
		return buildTypeAtom.isPseudoLocalesEnabled();
	}

	@Override
	public boolean isJniDebuggable() {
		return buildTypeAtom.isJniDebuggable();
	}

	@Override
	public boolean isRenderscriptDebuggable() {
		return buildTypeAtom.isRenderscriptDebuggable();
	}

	@Override
	public int getRenderscriptOptimLevel() {
		return buildTypeAtom.getRenderscriptOptimLevel();
	}

	@Override
	public boolean isMinifyEnabled() {
		return buildTypeAtom.isMinifyEnabled();
	}

	@Override
	public boolean isZipAlignEnabled() {
		return buildTypeAtom.isZipAlignEnabled();
	}

	@Override
	public boolean isEmbedMicroApp() {
		return buildTypeAtom.isEmbedMicroApp();
	}

	@Override
	public SigningConfig getSigningConfig() {
		return buildTypeAtom.getSigningConfig();
	}

}
