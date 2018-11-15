/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.sdkuilib.internal.repository.content;

/**
 * Information about a package type
 */
public class MetaPackage {

	/** Package type */
	private final PackageType mPackageType;
	/** Icon identified by name of image file */
	private final String mIconResource;

	/**
	 * Construct MetaPackage object
	 * @param packageType Package type
	 * @param iconResource Icon identified by name of image file
	 */
	public MetaPackage(PackageType packageType, String iconResource) {
		this.mPackageType = packageType;
		this.mIconResource = iconResource;
	}

	/**
	 * Returns package type
	 * @return PackageType enum
	 */
	public PackageType getPackageType() {
		return mPackageType;
	}

	/**
	 * Returns image file name
	 * @return String
	 */
	public String getIconResource() {
		return mIconResource;
	}

	/**
	 * Returns name of package type in format used by Android
	 * @return String
	 */
	public String getName()
	{
		return mPackageType.toString().replaceAll("_", "-");
	}
}
