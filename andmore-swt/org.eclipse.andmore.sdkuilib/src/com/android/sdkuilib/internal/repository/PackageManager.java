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

import java.util.Map;

import org.eclipse.andmore.sdktool.SdkContext;

import com.android.annotations.NonNull;
import com.android.repository.api.Downloader;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileSystemFileOp;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdkuilib.internal.repository.download.FileDownloader;
import com.google.common.collect.Maps;

/**
 * Package manager loads and installs packages
 * @author Andrew Bowley
 *
 * 12-12-2017
 */
public class PackageManager {

	/**
	 * Callback to handle LocalPackage loaded
	 */
	public interface LocalPackageHandler
	{
		void onPackageLoaded(LocalPackage localPackage);
	}
	
	/**
	 * Callback to handle RemotePackage loaded
	 */
	public interface RemotePackageHandler
	{
		void onPackageLoaded(RemotePackage remotePackage);
	}
	
	/**
	 * Callback to handle UpdateablePackage loaded
	 */
	public interface UpdatablePackageHandler
	{
		void onPackageLoaded(UpdatablePackage updatablePackage);
	}
	
    /**
     * Map from {@code path} (the unique ID of a package) to {@link UpdatablePackage}, including all
     * packages installed or available.
     */
    private final Map<String, UpdatablePackage> consolidatedPkgs = Maps.newTreeMap();
    /** SDK context */
    private final SdkContext sdkContext;
    /** Downloads files from online repositories */
    private final FileDownloader downloader;
    /** Container for all loaded packages */
    private RepositoryPackages packages;

    /**
     * Construct PackageManager object
     * @param sdkContext SDK context
     */
    public PackageManager(SdkContext sdkContext)
    {
    	this.sdkContext = sdkContext;
    	downloader = downloaderInstance();
    }

    /**
     * Returns downloader
     * @return Downloader object
     */
    public Downloader getDownloader()
    {
    	return downloader;
    }
 
    /**
     * Returns package by using it's path as the key
     * @param id Package path
     * @return UpdateablePackage object
     */
    public UpdatablePackage getPackageById(String id) 
    {
    	return consolidatedPkgs.get(id);
    }

    /**
     * Load local packages
     * @param handler Packages callback
     * @return flag set true if load completed successfully
     */
    public boolean loadLocalPackages(LocalPackageHandler handler)
    {
    	sdkContext.getRepoManager().loadSynchronously(0, sdkContext.getProgressIndicator(), null, null);
        if (sdkContext.hasError())
        	return false;
        Map<String, LocalPackage> localPackages = getRepositoryPackages(false).getLocalPackages();
        for (LocalPackage local : localPackages.values()) 
            handler.onPackageLoaded(local);
        return localPackages.size() > 0;
    }
    
    /**
     * Load remote packages
     * @param handler Packages callback
     * @return flag set true if load completed successfully
     */
    public boolean loadRemotePackages(RemotePackageHandler handler)
    {
    	sdkContext.getRepoManager().loadSynchronously(0, sdkContext.getProgressIndicator(), downloader, sdkContext.getSettings());
        if (sdkContext.hasError())
        	return false;
        Map<String, RemotePackage> remotePackages = getRepositoryPackages(false).getRemotePackages();
        for (RemotePackage remote : remotePackages.values()) 
            handler.onPackageLoaded(remote);
        return remotePackages.size() > 0;
    }
    
    /**
     * Load consolidated packages
     * @param handler Packages callback
     * @return flag set true if load completed successfully
     */
    public boolean loadConsolidatedPackages(UpdatablePackageHandler handler)
    {
        if (sdkContext.hasError())
        	return false;
        consolidatedPkgs.clear();
        consolidatedPkgs.putAll(getRepositoryPackages(true).getConsolidatedPkgs());
        for (UpdatablePackage updatable : consolidatedPkgs.values()) 
            handler.onPackageLoaded(updatable);
        return consolidatedPkgs.size() > 0;
    }

    /**
     * Returns object containing all packages
     * @param force Flag set true if packages are to be reloaded
     * @return RepositoryPackages object
     */
    public RepositoryPackages getRepositoryPackages(boolean force)
    {
    	if ((packages == null) || force)
    	{
    		RepoManager repoManager = sdkContext.getRepoManager();
    		repoManager.loadSynchronously(0, sdkContext.getProgressIndicator(), downloader, sdkContext.getSettings());
   		    packages =  repoManager.getPackages();
    	}
    	return packages;
    }
/*
    public abstract void load(long cacheExpirationMs,
            @Nullable List<RepoLoadedCallback> onLocalComplete,
            @Nullable List<RepoLoadedCallback> onSuccess,
            @Nullable List<Runnable> onError,
            @NonNull ProgressRunner runner,
            @Nullable Downloader downloader,
            @Nullable SettingsController settings,
            boolean sync);
 */
    /**
     * Request asynchronous package load
     * @param loadPackagesRequest Request parameters
     */
    public void requestRepositoryPackages(LoadPackagesRequest loadPackagesRequest)
	{
		RepoManager repoManager = sdkContext.getRepoManager();
		repoManager.load(0, 
				loadPackagesRequest.getOnLocalComplete(), 
				loadPackagesRequest.getOnSuccess(), 
				loadPackagesRequest.getOnError(), 
				loadPackagesRequest.getRunner(), 
				downloader, 
				sdkContext.getSettings(), 
				false);
	}

    /**
     * Set repository packages
     * @param packages Repository packages
     */
	public void setPackages(RepositoryPackages packages) {
		this.packages = packages;
	}

	/**
	 * Download and install given remote package
	 * @param remotePackage Package to install
	 * @return flag set true if operation succeeded
	 */
	protected boolean applyPackageOperation(RemotePackage remotePackage) {
    	ProgressIndicator progressIndicator = sdkContext.getProgressIndicator();
		@NonNull PackageOperation operation = 
			SdkInstallerUtil.findBestInstallerFactory(remotePackage, sdkContext.getHandler())
                .createInstaller(remotePackage, sdkContext.getRepoManager(), downloader, sdkContext.getFileOp());
		downloader.registerPackage(remotePackage, progressIndicator);
        return operation.prepare(progressIndicator) && operation.complete(progressIndicator);
    }

	/**
	 * Returns Downloader instance 
	 * @return FileDownloader object
	 */
	private FileDownloader downloaderInstance()
    {
        FileSystemFileOp fop = (FileSystemFileOp)FileOpUtils.create();
        return new FileDownloader(fop, sdkContext.getSettings());
    }
}
