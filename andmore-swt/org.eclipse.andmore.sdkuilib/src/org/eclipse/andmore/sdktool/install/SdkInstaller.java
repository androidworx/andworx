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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.andmore.sdktool.SdkCallAgent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.sdkuilib.internal.repository.content.PackageType;
import com.android.sdkuilib.repository.SdkUpdaterWindow;
import com.android.sdkuilib.repository.SdkUpdaterWindow.SdkInvocationContext;
import com.android.utils.ILogger;

/**
 * Interactive SDK package installer which limits initial selection to packages required for latest SDK platform
 * @author Andrew Bowley
 *
 * 05-12-2017
 */
public class SdkInstaller {
	protected static final String CREATE_DIRECTORY_FAILED = "Failed to create directory %1$s";
	
	public static final PackageType[] PLATFORM_PACKAGE_TYPES = {
			PackageType.tools,
			PackageType.platform_tools,
			PackageType.build_tools,
			PackageType.platforms
	};
	/** Most recent installation SDK location. May be null. */
    File sdkLocation;
    /** Android SDK preferences */
    private final AndroidSdkPreferences androidSdkPreferences;
	/** Flag set true if workspace preferences hsa SDK location configured */
	private boolean hasWorkspaceSdk;
	/** Persisted logger */
	private final ILogger consoleLogger;
	/** User logger */
	private ILogger displayLogger;
	/** Android SDK handler for current location */
    private AndroidSdkHandler handler;
    private List<RemotePackage> packagesInstalled;
    /** Progress indicator set only when SDK install job is running */
    private volatile ProgressIndicator sdkProgressIndicator;

    /**
     * Create SdkInstaller object
     * @param adtPrefs Main Android preferences in workspace
     * @param consoleLogger Persisted logger
     */
	public SdkInstaller(ILogger consoleLogger) {
		this.consoleLogger = consoleLogger;
		androidSdkPreferences = AndworxFactory.instance().getAndroidSdkPreferences();
		String workSpaceSdkLocation = getWorkSpaceSdk();
		hasWorkspaceSdk = (workSpaceSdkLocation != null) && !workSpaceSdkLocation.trim().isEmpty();
		if (hasWorkspaceSdk) {
			sdkLocation = new File(workSpaceSdkLocation.trim());
			hasWorkspaceSdk = sdkLocation.exists() && sdkLocation.isDirectory();
		}
	}

	/**
	 * Returns flag set true if workspace ADT preferences contains a SDK folder value
	 * @return boolean
	 */
	public boolean hasWorkspaceSdk() {
		return hasWorkspaceSdk;
	}

	/**
	 * Returns SDK location configured in workspace preferences
	 * @return
	 */
	public String getWorkSpaceSdk() {
		return androidSdkPreferences.getSdkLocationValue();
	}

	/**
	 * Returns persisted logger
	 * @return ILogger object
	 */
	public ILogger getConsoleLogger() {
		return consoleLogger;
	}

	/**
	 * Returns composite persisted and display loggers
	 * @return ILogger object
	 */
	public ILogger getLogger() {
		if (displayLogger == null)
			return consoleLogger;
		return getCompositeLogger();
	}

	/**
	 * Set display logger - seen by user
	 * @param displayLogger ILogger object
	 */
	public void setDisplayLogger(ILogger displayLogger) {
		this.displayLogger = displayLogger;
	}

	/**
	 * Returns most recent SDK location or null if this value is invalid or no installation has started
	 * @return SDK location as File object
	 */
	public File getSdkLocation() {
		return sdkLocation;
	}

	List<RemotePackage> getPackagesInstalled() {
		return packagesInstalled != null ? packagesInstalled : Collections.emptyList();
	}
	
	/**
	 * Install SDK allowing user to select from existing alternatives, if available.
	 * @param androidSdk Android SDK specification including location as File object
	 * @param sdkInstallListener Callback for handling completion of installation task
	 * @param usePacketFilter Flag set true if initial selections are for platforms only
	 */
	public void doInstall(AndroidSdk androidSdk, Shell shell, SdkInstallListener sdkInstallListener, boolean usePacketFilter) {
		// Clear sdkLocation field so it can indication successful installation\
		sdkLocation = androidSdk.getSdkLocation();
		Job job = new Job("Install SDK") {

			@Override
			protected IStatus run(IProgressMonitor progressMonitor) {
				ILogger logger = getLogger();
				// Create SDK location if it does not exist
				File sdkLocation = androidSdk.getSdkLocation();
				try {
			        if (!sdkLocation.isDirectory()) {
			            if (!sdkLocation.mkdirs()) {
			            	logger.error(null,  CREATE_DIRECTORY_FAILED, sdkLocation.getAbsolutePath());
							return Status.CANCEL_STATUS;
			            }
			        }
				} catch (SecurityException e) {
	            	logger.error(e,  CREATE_DIRECTORY_FAILED, sdkLocation.getAbsolutePath());
					return Status.CANCEL_STATUS;
				}
				try {
					// Recycle SDK handler if appropriate, otherwise get new instance
					if ((handler == null) || !handler.getAndroidFolder().equals(sdkLocation))
					    handler = AndroidSdkHandler.getInstance(sdkLocation);
					// Create call agent to set up an SDK context and launch SDK manager
			        SdkCallAgent sdkCallAgent = 
			        	new SdkCallAgent(
			        		handler,
			                logger);
			        SdkUpdaterWindow window = new SdkUpdaterWindow(
			                shell,
			                sdkCallAgent,
			                SdkInvocationContext.IDE);
			        if (usePacketFilter) {
			        // Use package filter to restrict initial slection to latest plaform. The user can change this if desired.
				        for (PackageType packageType: PLATFORM_PACKAGE_TYPES)
				        	window.addPackageFilter(packageType);
			        }
			        sdkProgressIndicator = sdkCallAgent.getSdkContext().getProgressIndicator();
			        Display.getDefault().syncExec(new Runnable(){

						@Override
						public void run() {
					        window.open();
					        packagesInstalled = window.getPackagesInstalled();
						}});
					return Status.OK_STATUS;
				} catch (Exception e) {
					logger.error(e, String.format("New SDK installation to %s failed", sdkLocation));
				} finally {
					sdkProgressIndicator = null;
				}
				
				return Status.CANCEL_STATUS;
			}
		};
		if (sdkInstallListener != null)
			job.addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent event) {
					if (getPackagesInstalled().size() > 0)
						sdkInstallListener.onSdkInstall(androidSdk);
					else
						sdkInstallListener.onCancel();
				}	
			});
		job.setPriority(Job.BUILD);
		job.schedule();
	}

	/**
	 * Returns list of Android SDKS given list of Android SDKs to verify. Only items with valid locations are returned.
	 * @param androidSdks
	 * @return
	 */
	public List<AndroidSdk> selectValidSdks(List<AndroidSdk> androidSdks) {
		List<AndroidSdk> sdkList = null;
		for (AndroidSdk androidSdk: androidSdks) {
			File sdkPath = androidSdk.getSdkLocation();
			if (sdkPath.exists() && sdkPath.isDirectory()) {
				if (sdkList == null)
					sdkList = new ArrayList<>();
				sdkList.add(androidSdk);
			}
		}
		return sdkList != null ? sdkList : Collections.emptyList();
	}
	
	/**
	 * Returns list of files given list of locations to verify. Only valid locations are returned.
	 * @param paths
	 * @return
	 */
	public List<File> selectValidPaths(List<String> paths) {
		List<File> fileList = new ArrayList<>();
		for (String location: paths) {
			if ((location != null) && !location.trim().isEmpty()) {
				File sdkPath = new File(location);
				if (sdkPath.exists() && sdkPath.isDirectory())
					fileList.add(sdkPath);
			}
		}
		return fileList;
	}

	/**
	 * Returns repository manager of current SDK handler or null if not available
	 * @return RepoManager object
	 */
	public RepoManager getRepoManager() {
		RepoManager repoManager = null;
		if (handler != null)
			repoManager = handler.getSdkManager(getProgress());
		return repoManager;
	}

	/**
	 * Cancel requested
	 */
	public void cancel() {
		if (sdkProgressIndicator != null)
			// Signal cancellation to install task
			sdkProgressIndicator.cancel();
		
	}

	public void updateSdkLocation(File sdkLocation) throws IOException {
        AndroidSdkPreferences sdkPrefs = AndworxFactory.instance().getAndroidSdkPreferences();
        sdkPrefs.setSdkLocation(sdkLocation);
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
		return targetManager.getTargets(getProgress());
	}


	/**
	 * Returns composite of console logger and display logger.
	 * Only errors and warnings are directed to the console
	 * @return
	 */
	private ILogger getCompositeLogger() {
		return (new ILogger(){

			@Override
			public void error(Throwable throwable, String message, Object... args) {
				consoleLogger.error(throwable, message, args);
				displayLogger.error(throwable, message, args);
			}

			@Override
			public void info(String message, Object... args) {
				//consoleLogger.info(message, args);
				displayLogger.info(message, args);
			}

			@Override
			public void verbose(String message, Object... args) {
				// TODO - Option for verbose logging as installation of SDK creates
				// huge log
				//consoleLogger.verbose(message, args);
				//displayLogger.verbose(message, args);
			}

			@Override
			public void warning(String message, Object... args) {
				consoleLogger.warning(message, args);
				displayLogger.warning(message, args);
			}});
	}

	/**
	 * Returns Target Manager
	 * @return AndroidTargetManager object
	 */
	private AndroidTargetManager getTargetManager(File sdkLocation) {
		if ((handler == null) || !handler.getLocation().equals(sdkLocation))
		    handler = AndroidSdkHandler.getInstance(sdkLocation);
		return handler.getAndroidTargetManager(getProgress());
	}

	private ProgressIndicator getProgress() {
		return new ProgressIndicatorAdapter(){
		    @Override
		    public void logWarning(@NonNull String s) {
		    	consoleLogger.warning(s);
		    }

		    @Override
		    public void logWarning(@NonNull String s, @Nullable Throwable e) {
		    	consoleLogger.warning(s);
		    }

		    @Override
		    public void logError(@NonNull String s) {
		    	consoleLogger.error(null, s);
		    }

		    @Override
		    public void logError(@NonNull String s, @Nullable Throwable e) {
		    	consoleLogger.error(e, s);
		    }

		    @Override
		    public void logInfo(@NonNull String s) {
		    	consoleLogger.info(s);
		    }
		};
	}

}

