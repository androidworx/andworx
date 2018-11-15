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
package org.eclipse.andworx.sdk;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;

import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.log.SdkLogger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.sdk.LoadStatus;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.repository.api.RepoManager;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;
import com.google.common.base.Throwables;

/**
 * Information on an Android SDK installation at a particular location 
 * and resources in the Android environment. Also implements Android SDK ILogger interface.
 */
public class SdkProfile extends AndroidEnvironment implements ILogger {
	
	public static final String SDK_NOT_AVAILABLE_ERROR = "Android SDK not available";
	private static SdkLogger logger = SdkLogger.getLogger(SdkProfile.class.getName());

	/** Interim TargetLoadStatusMonitor to be replaced by actual Target Data container */
	private class LoadingTargetLoadStatus implements TargetLoadStatusMonitor {

		private TargetLoadStatusMonitor replacement;
		
		@Override
		public LoadStatus getLoadStatus(String hashString) {
			if (replacement != null)
				return replacement.getLoadStatus(hashString);
			LoadStatus loadStatus = LoadStatus.FAILED;
	   		if (isValid()) {
				for (IAndroidTarget target: getAndroidTargets()) {
					if (hashString.equals(AndroidTargetHash.getTargetHashString(target))) {
						loadStatus = LoadStatus.LOADING;
						break;
					}
				}
			}
	 	    return loadStatus; 
		}
		
        void setReplacement(TargetLoadStatusMonitor replacement) {
        	this.replacement = replacement;
        	synchronized (this) {
        		notifyAll();
        	}
        }
	}

	/** Android SDK interface to {@link RepoManager} */
	private AndroidSdkHandler androidSdkHandler;
	/** Primary interface for interacting with repository packages */
	private RepoManager manager;
	/** Android Virtual Device Manager to manage AVDs */
	private AvdManager avdManager;
	/** Device manager */
	private DeviceManager deviceManager;
	/** Location of Android SDK installation being profiled */
	private File sdkDirectory;

	/**
	 * Create SdkProfile object. Use {@link #isValid()} to determine if the location is valid.
	 * @param sdkLocation Absolute location of Android SDK installation being profiled
	 */
	public SdkProfile(String sdkLocation) {
		this(new File(sdkLocation));
	}

	/**
	 * Create SdkProfile object. Use {@link #isValid()} to determine if the location is valid.
	 * @param androidEnvironment Android environment containing location of SDK installation being profiled
	 */
	public SdkProfile(AndroidEnvironment androidEnvironment) {
		this(androidEnvironment.getAndroidSdkHandler().getLocation());
	}

	/**
	 * Create SdkProfile object. Use {@link #isValid()} to determine if the location is valid.
	 * @param sdkDirectory File object containing location of Android SDK installation being profiled
	 */
	public SdkProfile(File sdkDirectory) {
		super(logger);
		if (!sdkDirectory .exists()) {
			error(null, "Android SDK", sdkDirectory.getAbsoluteFile() + " not found");
			sdkDirectory = null;
			return;
		}
		targetLoadStatusMonitor = new LoadingTargetLoadStatus();
        // SdkManager object for the location
        androidSdkHandler = AndroidSdkHandler.getInstance(sdkDirectory);
        // If lastest build tool not available, do not proceed
        BuildToolInfo buildToolInfo = androidSdkHandler.getLatestBuildTool(getProgressIndicator(), true /*allowPreview*/);
            if (buildToolInfo == null) {
                error(null, SDK_NOT_AVAILABLE_ERROR);
                AndroidSdkHandler.resetInstance(sdkDirectory);
            } else {
                manager = androidSdkHandler.getSdkManager(getProgressIndicator());
                // create the AVD Manager
                try {
                    avdManager = AvdManager.getInstance(androidSdkHandler, this);
                } catch (AndroidLocationException e) {
                    error(e, "Error parsing the AVDs");
                }
                deviceManager = DeviceManager.createInstance(androidSdkHandler, this);
            }
	}
	
	/**
	 * Returns flag set true if location being profiled is valid
	 * @return boolean
	 */
	@Override
	public boolean isValid() {
		return androidSdkHandler != null && manager != null;
	}

	/**
	 * Returns primary interface for interacting with repository packages 
	 * @return RepoManager object
	 */
	public RepoManager getManager() {
		return manager;
	}

	/**
	 * Returns Android Virtual Device Manager to manage AVDs
	 * @return AvdManager object
	 */
	public AvdManager getAvdManager() {
		return avdManager;
	}

	/**
	 * Returns SDK device manager
	 * @return DeviceManager object
	 */
	public DeviceManager getDeviceManager() {
		return deviceManager;
	}

	/**
	 * Returns location of Android SDK installation being profiled 
	 * @return File object
	 */
	public File getSdkDirectory() {
		return sdkDirectory;
	}

	/**
	 * Returns target assigned to given Android Virtual Device
	 * @param avdInfo AVD information
	 * @return IAndroidTarget object
	 */
    public IAndroidTarget getAndroidTargetFor(AvdInfo avdInfo) {
    	 ProgressIndicator progressIndicator = getProgressIndicator();
         return androidSdkHandler.getAndroidTargetManager(progressIndicator)
                .getTargetOfAtLeastApiLevel(avdInfo.getAndroidVersion().getApiLevel(), progressIndicator);
    }

	/**
	 * Returns highest available Target Platform
	 * @return IAndroidTarget object
	 */
	public IAndroidTarget getHighestTarget() {
		Collection<IAndroidTarget> targets = getAndroidTargets();
		IAndroidTarget[] sdkTargets = targets.toArray(new IAndroidTarget[targets.size()]);
		for (int i = targets.size() - 1; i >= 0; --i)
			if (sdkTargets[i].isPlatform())
				return sdkTargets[i];
		throw new AndworxException(SDK_NOT_AVAILABLE_ERROR);
	}
	
    /**
	 * Returns progress indicator to use for for logging only
	 * @return ProgressIndicator object
	 */
	public ProgressIndicator getProgressIndicator() {
		return new ProgressIndicatorAdapter() {
		    @Override
		    public void logWarning(@NonNull String s) {
		        logWarning(s, null);
		    }

		    @Override
		    public void logWarning(@NonNull String s, @Nullable Throwable e) 
		    {
		    	String message;
		    	if (e != null)
		    		message = s + "\n" + Throwables.getStackTraceAsString(e);
		    	else
		    		message = s;
		    	warning(message);
		    }

		    @Override
		    public void logError(@NonNull String s) {
		        error(null, s);
		    }

		    @Override
		    public void logError(@NonNull String s, @Nullable Throwable e) 
		    {
		    	error(e, s);
		    }
		};
	}

	/**
	 * Replace interim TargetLoadStatusMonitor
	 * @param monitor Actual Target Data container which implements monitor interface
	 */
	public void setTargetLoadStatusMonitor(TargetLoadStatusMonitor monitor) {
		if (targetLoadStatusMonitor instanceof LoadingTargetLoadStatus) {
			// Expected behavior
			((LoadingTargetLoadStatus)targetLoadStatusMonitor).setReplacement(monitor);
		}
		targetLoadStatusMonitor = monitor;
	}
	
	/**
	 * Returns Android SDK interface to {@link RepoManager}
	 * @return AndroidSdkHandler object
	 */
	@Override
	public AndroidSdkHandler getAndroidSdkHandler() {
		if (!isValid())
			throw new AndworxException(SDK_NOT_AVAILABLE_ERROR);
		return androidSdkHandler;
	}

	/**
	 * Returns closest match Target Platform to given version
	 * @param targetHash Target platform version specified as a hash string
	 * @return IAndroidTarget object
	 */
	@Override
	public IAndroidTarget getAvailableTarget(String targetHash) {
		return matchTarget(targetHash);
	}
	
	/**
	 * Returns the available Android targets (both Platform and Add-ons
	 * @return Android target collection
	 */
	@Override
	public Collection<IAndroidTarget> getAndroidTargets() {
		ProgressIndicator progressIndicator = getProgressIndicator();
		Collection<IAndroidTarget> targets = getAndroidSdkHandler()
		.getAndroidTargetManager(progressIndicator)
		.getTargets(progressIndicator);
		if (targets == null)
			targets = Collections.emptyList();
		return targets;
	}

    /**
     * Prints an error message.
     *
     * @param throwable is an optional {@link Throwable} or {@link Exception}. If non-null, its
     *          message will be printed out.
     * @param format is an optional error format. If non-null, it will be printed
     *          using a {@link Formatter} with the provided arguments.
     * @param args provides the arguments for errorFormat.
     */
	@Override
	public void error(Throwable throwable, String format, Object... args) {
		AndworxBuildPlugin.instance().logAndPrintError(throwable, logger.getName(), format, args);
	}

    /**
     * Prints an information message.
     *
     * @param format is a string format to be used with a {@link Formatter}. Cannot be null.
     * @param args provides the arguments for msgFormat.
     */
	@Override
	public void info(String format, Object... args) {
		logger.info(format, args);
	}

    /**
     * Prints a verbose message.
     *
     * @param format is a string format to be used with a {@link Formatter}. Cannot be null.
     * @param args provides the arguments for msgFormat.
     */
	@Override
	public void verbose(String format, Object... args) {
		logger.verbose(format, args);
	}

    /**
     * Prints a warning message.
     *
     * @param format is a string format to be used with a {@link Formatter}. Cannot be null.
     * @param args provides the arguments for warningFormat.
     */
	@Override
	public void warning(String format, Object... args) {
		logger.warning(format, args);
	}

	/**
	 * Returns first available Target Platform with version equal to or greater than that given
	 * @param targetHashString Target platform version identified as hash string eg. "android-27"
	 * @return IAndroidTarget object
	 */
	private IAndroidTarget matchTarget(String targetHashString) {
    	AndroidVersion androidVersion = AndroidTargetHash.getPlatformVersion(targetHashString);
    	for (IAndroidTarget sdkTarget: getAndroidTargets()) {
    		if (sdkTarget.getVersion().isGreaterOrEqualThan(androidVersion.getApiLevel())) {
    			return sdkTarget;
    		}
    	}
    	return getHighestTarget();
    }


}
