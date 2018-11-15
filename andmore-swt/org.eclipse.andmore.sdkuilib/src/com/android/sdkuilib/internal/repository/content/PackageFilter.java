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
/**
 * 
 */
package com.android.sdkuilib.internal.repository.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType;

/**
 * PackageFilter provides functions to select categories and and package items 
 * based on user provided set of package types
 * @author Andrew Bowley
 *
 * 24-11-2017
 */
public class PackageFilter {

	public static Set<PackageType> EMPTY_PACKAGE_TYPE_SET;
	public static final PackageType[] GENERIC_PACKAGE_TYPES; 
	private static final String BLANK = "";
	
	static 
	{
		EMPTY_PACKAGE_TYPE_SET = Collections.emptySet();
		GENERIC_PACKAGE_TYPES = new PackageType[] {
				PackageType.emulator,
				PackageType.cmake,
				PackageType.docs,
				PackageType.lldb,
				PackageType.ndk_bundle,
				PackageType.patcher,
				PackageType.generic,
				PackageType.samples
		};
	}
	
	/** Set of package types on which to filter. An empty set indicates no filtering */
	private Set<PackageType> packageTypeSet;
	/** Tag to filter on for packages like system image which have a tag */
	private String tag;
	// Flags to support filter logic
	private boolean selectTools;
	private boolean selectApi;
	private boolean selectExtra;
	private boolean selectGeneric;
	
	/**
	 * Default constructor
	 */
	public PackageFilter() {
		packageTypeSet = EMPTY_PACKAGE_TYPE_SET; 
	}

	/**
	 * Construct PackageFilter object with given selection set
	 */
	public PackageFilter(Set<PackageType> packageTypes) {
		if ((packageTypes == null) || (packageTypes.isEmpty()))
			packageTypeSet = EMPTY_PACKAGE_TYPE_SET; 
		else {
			packageTypeSet = new TreeSet<>();
			packageTypeSet.addAll(packageTypes);
			initialize();
		}
	}

	/**
	 * Set to filter on given package types
	 * @param packageTypeSet Set of package types
	 */
	public void setPackageTypes(Set<PackageType> packageTypeSet) {
		if (this.packageTypeSet.isEmpty())
			this.packageTypeSet = new TreeSet<>();
		this.packageTypeSet.clear();
		this.packageTypeSet.addAll(packageTypeSet);
		initialize();
	}

	/**
	 * Returns current filter set
	 * @return Set of package types
	 */
	public Set<PackageType> getPackageTypes() {
		return Collections.unmodifiableSet(packageTypeSet);
	}

	/**
	 * Returns flag set true if filtering on at least one package type
	 * @return
	 */
	public boolean isFilterOn() {
		return !packageTypeSet.isEmpty();
	}

	/**
	 * @return the tag
	 */
	public String getTag() {
		return tag == null ? BLANK : tag;
	}

	/**
	 * @param tag the tag to set
	 */
	public void setTag(String tag) {
		this.tag = tag;
	}

	/**
	 * Returns selection of categories for given list. If filtering is off, returns given list unchanged.
	 * @param cats PkgCategory list
	 * @return PkgCategory list
	 */
	public List<PkgCategory<AndroidVersion>> getFilteredApiCategories(List<PkgCategory<AndroidVersion>> cats) {
		if (!isFilterOn())
			return cats;
		List<PkgCategory<AndroidVersion>> selectCategories = new ArrayList<>();
		for (PkgCategory<AndroidVersion> cat: cats) {
			CategoryKeyType catKeyType = cat.getKeyType();
			switch(catKeyType) {
			case TOOLS:
			case TOOLS_PREVIEW: 
				if (selectTools)
					selectCategories.add(cat);
				break;
			case API: 
			case EARLY_API: 
				if (selectApi)
					selectCategories.add(cat);
				break;
			case EXTRA: 
				if (selectExtra)
					selectCategories.add(cat);
				break;
			case GENERIC: 
				if (selectGeneric)
				    selectCategories.add(cat);
				break;
			default:
				break;
			}
		}
		return selectCategories;
	}

	/**
	 * Returns flag set true if given node is a PkgItem object which meets filter criteria
	 * @param node
	 * @return
	 */
	public boolean selectItem(INode node)
	{
		if ((node == null) || !(node instanceof PkgItem))
			return false;
		PkgItem packageItem = (PkgItem)node;
		PackageType packageType = packageItem.getMetaPackage().getPackageType();
		if (packageTypeSet.contains(packageType)) {
			if ((packageType == PackageType.system_images) && (tag != null) && !tag.isEmpty()) {
				TypeDetails typeDetails = packageItem.getMainPackage().getTypeDetails();
				if (typeDetails instanceof SysImgDetailsType) {
					SysImgDetailsType sysImageType = (SysImgDetailsType)typeDetails;
						return tag.equals(sysImageType.getTag().getId());
				}
			}
			return true;
		}
		if (selectGeneric) {
			for (int i = 0; i < GENERIC_PACKAGE_TYPES.length; ++i)
				if (GENERIC_PACKAGE_TYPES[i] == packageType)
					return true;
		}
		return false;
	}

	/**
	 * Initialize flags for filter logic
	 */
	private void initialize()
	{
		if (!isFilterOn())
			return;
		selectTools = selectApi = selectExtra = selectGeneric = false;
		selectTools = 
				packageTypeSet.contains(PackageType.build_tools) ||
				packageTypeSet.contains(PackageType.platform_tools) ||
				packageTypeSet.contains(PackageType.tools);
		selectApi =
				packageTypeSet.contains(PackageType.platforms) ||
				packageTypeSet.contains(PackageType.add_ons) ||
				packageTypeSet.contains(PackageType.system_images) ||
		        packageTypeSet.contains(PackageType.sources);
		selectExtra = packageTypeSet.contains(PackageType.extras);
		
		for (int i = 0; i < GENERIC_PACKAGE_TYPES.length; ++i)
			if (packageTypeSet.contains(GENERIC_PACKAGE_TYPES[i])) {
				selectGeneric = true;
				break;
			}
	}
}
