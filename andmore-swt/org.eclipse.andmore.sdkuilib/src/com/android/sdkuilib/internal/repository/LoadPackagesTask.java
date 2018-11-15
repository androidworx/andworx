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

package com.android.sdkuilib.internal.repository;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager.RepoLoadedCallback;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.meta.DetailsTypes.PlatformDetailsType;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser;
import com.android.sdkuilib.internal.repository.content.PackageFilter;
import com.android.sdkuilib.internal.repository.content.PackageType;
import com.android.sdkuilib.internal.repository.ui.SdkProgressFactory;
import com.android.sdkuilib.internal.tasks.ILogUiProvider;

/**
 * LoadPackagesTask builds the repository data of the SDK handler. This involves calling to online sites and
 * can take a while depending on line speed and host responsiveness. The class is abstract to allow a sub class to
 * implement a callback handler for the "task completed" event. This approach is used because the Android library
 * doing the actual package load passes control back to the caller using the RepoLoadedCallback interface. Somehow 
 * the RepoLoadedCallback implementation must itself iniatiate a final callback. 
 * @author Andrew Bowley
 *
 * 16-12-2017
 */
public abstract class LoadPackagesTask implements RepoLoadedCallback, Runnable  {
	protected static final String LOAD_ERROR = "Package operation did not complete due to error or cancellation";
	protected static final String NO_ANDROID_PLATFORM = "No Android Platform is installed.";
	protected static final String LOAD_COMPLETED = "Done loading packages.";

	/** Package manager loads and installs packages */
	private final PackageManager packageManager;
	/** PackageAnalyser builds and maintains the tree that underlies the SDK packages view */
    private final PackageAnalyser packageAnalyser;
    /** PackageFilter provides functions to select categories and and package items */
	private final PackageFilter packageFilter;
    /** A factory for various task types linked to a ProgressView control */
	private final SdkProgressFactory sdkProgressFactory;
	
	/**
	 * Construct a LoadPackagesTask object
	 * @param packageManager Builds and maintains the tree that underlies the SDK packages view
	 * @param packageAnalyser Builds and maintains the tree that underlies the SDK packages view
	 * @param packageFilter Provides functions to select categories and and package items
	 * @param sdkProgressFactory A factory for various task types linked to a ProgressView control
	 */
	public LoadPackagesTask(
			PackageManager packageManager, 
			PackageAnalyser packageAnalyser, 
			PackageFilter packageFilter, 
			SdkProgressFactory sdkProgressFactory) {
		this.packageManager = packageManager;
		this.packageAnalyser = packageAnalyser;
		this.packageFilter = packageFilter;
		this.sdkProgressFactory = sdkProgressFactory;
	}

	/**
	 * Callback to execute when package load is complete
	 */
	public abstract void onLoadComplete();

	/**
	 * {@link RepoLoadedCallback } implementation
	 * @param  packages Repository packages from successful load
	 */
	@Override
	public void doRun(RepositoryPackages packages) {
    	packageManager.setPackages(packages);
    	packageAnalyser.loadPackages();
    	// Ensure at least one platform package is loaded
	    Collection<LocalPackage> localPackages = packageManager.getRepositoryPackages(false).getLocalPackagesForPrefix(PackageType.platforms.toString());
	    boolean hasPlatform = false;
	    if (!localPackages.isEmpty()) {
	    	Iterator<LocalPackage> iterator = localPackages.iterator();
	    	while (iterator.hasNext())
	    		if (iterator.next().getTypeDetails() instanceof PlatformDetailsType) {
	    			hasPlatform = true;
	    			break;
	    		}
	    }
		ILogUiProvider progressControl = sdkProgressFactory.getProgressControl();
	    if (!hasPlatform) {
	    	progressControl.setDescription(NO_ANDROID_PLATFORM);
	        Set<PackageType> packageTypeSet = new TreeSet<>();
	        packageTypeSet.addAll(packageFilter.getPackageTypes());
	        packageTypeSet.add(PackageType.platforms);
	        packageFilter.setPackageTypes(packageTypeSet);
	        
	    } else {
	    	progressControl.setDescription(LOAD_COMPLETED);
	    }
	    onLoadComplete();
	}

	/**
	 * Error callback is just a Runnable
	 */
	@Override
	public void run() {
		ILogUiProvider progressControl = sdkProgressFactory.getProgressControl();
		progressControl.setDescription(LOAD_ERROR);
	}
	

}
