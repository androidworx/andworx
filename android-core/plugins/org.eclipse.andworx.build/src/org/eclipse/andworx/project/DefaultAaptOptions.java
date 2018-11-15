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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.android.builder.model.AaptOptions;

/**
 * AAPT options implementation - used as default
 */
public class DefaultAaptOptions implements AaptOptions {

	private List<String> additionalParameters;
	private boolean failOnMissingConfigEntry;
	private String ignoreAssets;
	
    public void setAdditionalParameters(List<String> additionalParameters) {
		this.additionalParameters = additionalParameters;
	}

	public void setFailOnMissingConfigEntry(boolean failOnMissingConfigEntry) {
		this.failOnMissingConfigEntry = failOnMissingConfigEntry;
	}

	public void setIgnoreAssets(String ignoreAssets) {
		this.ignoreAssets = ignoreAssets;
	}

	/**
     * Returns the value for the --ignore-assets option, or null
     */
	@Override
	public String getIgnoreAssets() {
		return ignoreAssets;
	}

    /**
     * Returns the list of values for the -0 (disabled compression) option, or null
     */
	@Override
	public Collection<String> getNoCompress() {
		return null;
	}

    /**
     * passes the --error-on-missing-config-entry parameter to the aapt command, by default false.
     */
	@Override
	public boolean getFailOnMissingConfigEntry() {
		return failOnMissingConfigEntry;
	}

    /**
     * Returns the list of additional parameters to pass.
     */
	@Override
	public List<String> getAdditionalParameters() {
		return additionalParameters != null ? additionalParameters : Collections.emptyList();
	}

    /**
     * Resources must be namespaced. Always required by default.
     *
     * <p>Each library is compiled in to an AAPT2 static library with its own namespace.
     *
     * <p>Projects using this <em>cannot</em> consume non-namespaced dependencies.
     */
	@Override
	public Namespacing getNamespacing() {
		return Namespacing.REQUIRED;
	}
}
