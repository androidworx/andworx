package org.eclipse.andworx.build;

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
import com.android.ide.common.build.ApkData;

/**
 * ApkVariantOutput is used to model outputs of a variant during configuration and it is sometimes altered at
 * execution when new pure splits are discovered.
 * TODO - Revise design to account for all the various types of APKs - refer gradle-core: com.android.build.gradle.internal.scope.OutputFactory
 */
public class ApkVariantOutput extends ApkData {

	private static final long serialVersionUID = 1L;

	/** Descriptive name given to filter to indicate what is selected. The name "universal" indicates no filtering */
	private String filterName;
	/** Variant base name */
	private String baseName;
	/** Variant full name */
	private String fullName;
	/** OutputType */
	private OutputType type;
	
    // Tthe main output should not have a dirName set as all the getXXXOutputDirectory
    // in variant scope already include the variant name.
	/** Name of directory to contain the output file - used inconsistently */
	private String dirName;

	/**
	 * Set filter name
	 * @param filterName
	 */
	public void setFilterName(String filterName) {
		this.filterName = filterName;
	}

	/**
	 * Set base name
	 * @param baseName
	 */
	public void setBaseName(String baseName) {
		this.baseName = baseName;
	}

	/**
	 * Set full name
	 * @param fullName
	 */
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	/**
	 * Set output type
	 * @param type OutputType enum
	 */
	public void setType(OutputType type) {
		this.type = type;
	}

	/**
	 * Set directory name
	 * @param dirName
	 */
	public void setDirName(String dirName) {
		this.dirName = dirName;
	}

	@Override
	public String getFilterName() {
		return filterName;
	}

	@Override
	public String getBaseName() {
		return baseName;
	}

	@Override
	public String getFullName() {
		return fullName;
	}

	@Override
	public OutputType getType() {
		return type;
	}

	@Override
	public String getDirName() {
		return dirName;
	}

}
