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

package org.eclipse.andmore.sdktool.install;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.sdkuilib.internal.repository.content.PackageAnalyser;
import com.android.sdkuilib.internal.repository.content.PackageType;
import com.android.utils.ILogger;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.repository.api.RepoManager;

/**
 * Obtains details of an SDK installation to profile working status.
 * Also acts as progress indicator to detect when an error causes the operation to fail.
 * @author Andrew Bowley
 *
 * 06-12-2017
 */
public class SdkProfile extends ProgressIndicatorAdapter {
	public final static Set<LocalPackage> EMPTY_LOCAL_PACKAGE_SET = Collections.emptySet();
	public final static EnumSet<PackageType> REQUIRED_PACKAGE_TYPES = 
		EnumSet.of(PackageType.platforms, PackageType.build_tools, PackageType.tools, PackageType.platform_tools);

	/** The SDK location to profile. This is required to be a directory that exists and has read access. */
	private File sdkPath;
	/** Set of required platform packages installed in SDK */
	private EnumSet<PackageType> packageTypes;
	/** All required platform package types found in SDK location */
	private Set<LocalPackage> platforms = EMPTY_LOCAL_PACKAGE_SET;
	/** Status message which is set if an error occurs */
	private String statusMessage;
	/** Android SDK handler used to load local packages */
    private AndroidSdkHandler handler;
    /** Flag set true if profile operation completes successfully */
    private boolean success;
    /** Persistent logger */
    private final ILogger consoleLogger;

	/**
	 * Construct a SdkProfile object
	 * @param consoleLogger Persistent logger
	 */
	public SdkProfile(ILogger consoleLogger) {
		this.consoleLogger = consoleLogger;
		statusMessage = "";
	}
	
	/**
	 * Returns SDK location
	 * @return SDK location as a File object. This will be empty if the location has not been set, but never null. 
	 */
	public File getSdkPath() {
		if (sdkPath == null) // Gracefully prevent NPE
			return new File("");
		return sdkPath;
	}

	/**
	 * Returns status message which is empty if no error occurred.
	 * @return String 
	 */
	public String getStatusMessage() {
		return statusMessage != null ? statusMessage : "";
	}

	/** 
	 * Returns flag set true if profile operation succeeded 
	 */
	public boolean getSuccess() {
		return success;
	}

	/**
	 * Returns set of required platform package types found in SDK location
	 * @return PackageType set
	 */
	public EnumSet<PackageType> getPackageTypes() {
		return packageTypes;
	}

	/**
	 * Returns number of outstanding required platform package types
	 * @return int
	 */
	public int getRequiredPackageCount() {
		if ((packageTypes == null) || platforms.isEmpty())
			return REQUIRED_PACKAGE_TYPES.size();
		return REQUIRED_PACKAGE_TYPES.size() - packageTypes.size();
	}

	/**
	 * Returns set of local platform packages installed in SDK location
	 * @return LocalPackage set
	 */
	public Set<LocalPackage> getPlatforms() {
		return platforms;
	}

	/**
	 * Evaluate profile asynchronously. If SDK Path exists, try to load packages.
	 * @param localPath SDK location
	 * @param sdkProfileListener Callback to complete profile task
	 */
	public void evaluate(File localPath, SdkProfileListener sdkProfileListener) {
		this.sdkPath = localPath;
		ProgressIndicator progressIndicator = this;
		Job job = new Job("Evaluate SDK profile") {

			@Override
			protected IStatus run(IProgressMonitor arg0) {
				try {
					if ((handler != null) && (handler.getAndroidFolder().equals(localPath)))
						AndroidSdkHandler.resetInstance(localPath);
					else
					    handler = AndroidSdkHandler.getInstance(localPath);
					success = true;
					RepoManager repoManager = handler.getSdkManager(progressIndicator);
					success = repoManager.loadSynchronously(0, progressIndicator, null, null);
					if (success ) {
						evaluate(repoManager);
						return Status.OK_STATUS;
					}
				} catch (Exception e) {
					statusMessage = e.getMessage();
					success = false;
				}
				return Status.CANCEL_STATUS;
			}
		};
		if (sdkProfileListener != null)
			job.addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent event) {
					sdkProfileListener.onProfile(SdkProfile.this);
				}	
			});
		job.setPriority(Job.BUILD);
		job.schedule();
	}

	/**
	 * Evaluate profile using given repository manager
	 * @param repoManager Repository manager with previously loaded packages
	 */
	public void evaluate(RepoManager repoManager)  {
		packageTypes = EnumSet.noneOf(PackageType.class);
		repoManager.loadSynchronously(0, this, null, null);
		Map<String, LocalPackage> packages = repoManager.getPackages().getLocalPackages();
		PackageType localPackageType = null;
		if (platforms.size() > 0)
			platforms.clear();
		else // Ensure Collections not being used
			platforms = new TreeSet<>();
		for (LocalPackage localPackage: packages.values()) {
	    	String name = PackageAnalyser.getNameFromPath(localPackage.getPath());
	    	for (PackageType packageType: PackageType.values()) {
	    		if (name.equals(packageType.name().replaceAll("_",  "-"))) {
	    			localPackageType = packageType;
	    			break;
	    		}
	    	}
	    	if ((localPackageType != null) && 
	    		REQUIRED_PACKAGE_TYPES.contains(localPackageType) &&
	    		!packageTypes.contains(localPackageType)) {
	    		packageTypes.add(localPackageType);
	    	}
	    	if ((localPackageType != null) && (localPackageType == PackageType.platforms)) {
	    		platforms.add(localPackage);
	    	}
		}
	}

	/**
	 * Returns all available targets
	 * @return IAndroidTarget collection - may be empty if SDK not available 
	 * @see {@link #getSystemImageTargets()}
	 */
	public Collection<IAndroidTarget> getTargets(File sdkLocation) {
		AndroidTargetManager targetManager = getTargetManager(sdkLocation);
		if (targetManager == null)
			return Collections.emptyList();
		return targetManager.getTargets(this);
	}


    @Override
    public void logWarning(@NonNull String message, @Nullable Throwable throwable) {
    	consoleLogger.warning(message, throwable);
    }
    
    @Override
    public void logError(@NonNull String message, @Nullable Throwable throwable) {
    	consoleLogger.error(throwable, message);
    	success = false;
    	statusMessage = message;
    }
    
    @Override
    public void logInfo(@NonNull String message) {
    	consoleLogger.info(message);
    }
    
    @Override
    public void logVerbose(@NonNull String message) {
    	consoleLogger.info(message);
    }

	/**
	 * Returns Target Manager
	 * @return AndroidTargetManager object
	 */
	private AndroidTargetManager getTargetManager(File sdkLocation) {
		if ((handler == null) || !handler.getLocation().equals(sdkLocation))
		    handler = AndroidSdkHandler.getInstance(sdkLocation);
		return handler.getAndroidTargetManager(this);
	}

}
