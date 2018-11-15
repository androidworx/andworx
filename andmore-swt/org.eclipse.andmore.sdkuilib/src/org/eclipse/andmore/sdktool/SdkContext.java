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
package org.eclipse.andmore.sdktool;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocation;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdkuilib.internal.repository.PackageManager;
import com.android.repository.api.RepositorySourceProvider;
import com.android.sdkuilib.internal.repository.Settings;
import com.android.utils.ILogger;

/**
 * Contains Android SDK Handler and associated managers. Also supports logging, progress indication, image creation and
 * other ancillary SDK support functions.
 * @author Andrew Bowley
 *
 * 30-12-2017
 */
public class SdkContext {

	/** Android SDK interface to {@link RepoManager}. Ensures that the proper android sdk-specific
      * schemas and source providers are registered, and provides android sdk-specific package logic */
    private AndroidSdkHandler handler;
    /** Repository manager */
    private RepoManager repoManager;
    /** Manager class for interacting with {@link Device}s within the SDK */
    private final DeviceManager deviceManager;
    /** Loads and installs packages */
    private final PackageManager packageManager;
    /** An image factory and broadcasts package installation events */
    private final SdkHelper sdkHelper;
    /** Flag set true if internal logger has warning */
    private final AtomicBoolean hasWarning = new AtomicBoolean();
    /** Flag set true if internal logger has error */
    private final AtomicBoolean hasError = new AtomicBoolean();
    /** Internal logger message store */
    private final ArrayList<String> logMessages = new ArrayList<String>();
    /** Settings implementation required by repository manager @see {@link SettingsController}*/
    private Settings settings;
    /** Progress indicator */
    private ProgressIndicator sdkProgressIndicator;
    /** Logger - defaults to internal implementation until an external one is set @see #setSdkLogger(ILogger) */
    private ILogger sdkLogger;
    /** Flag set true if SDK location has changed */
    private boolean sdkLocationChanged;
 
    /**
     * Construct SdkContext object
     * @param handler Android SDK Handler
     */
	public SdkContext(AndroidSdkHandler handler) {
		super();
		this.handler = handler;
		// Set logger post construction
		sdkLogger = loggerInstance();
		sdkProgressIndicator = getProgressIndicator();
		this.repoManager = handler.getSdkManager(sdkProgressIndicator);
		this.sdkHelper = new SdkHelper(sdkLogger);
        deviceManager = DeviceManager.createInstance(handler.getLocation(), sdkLogger);
        packageManager = new PackageManager(this);
	}

	/**
	 * Set settings required by Repository Manager
	 * @param settings The setting
	 */
	public void setSettings(Settings settings)
	{
		this.settings = settings;
	}

	/**
	 * Returns settings required by Repository Manager
	 * @return Settings object, which will contain defaults if {@link #setSettings(Settings)} not called previously
	 */
	public Settings getSettings()
	{
		if (settings == null)
			settings = new Settings(sdkLogger);
		return  settings;
	}

	/**
	 * Returns flag set true if SDK location has changed
	 * @return boolean
	 */
	public boolean isSdkLocationChanged() {
		return sdkLocationChanged;
	}

	/**
	 * Returns package manager
	 * @return PackageManager object
	 */
	public PackageManager getPackageManager()
	{
		return packageManager;
	}
	
	/**
	 * Returns SDK Helper - an image factory and broadcasts package installation events
	 * @return SdkHelper object
	 */
	public SdkHelper getSdkHelper()
	{
		return sdkHelper;
	}

	/**
	 * Returns  Android SDK Handler
	 * @return AndroidSdkHandler object
	 */
	public AndroidSdkHandler getHandler() {
		return handler;
	}

	/**
	 * Returns Repository Manager
	 * @return RepoManager object
	 */
	public RepoManager getRepoManager() {
		return repoManager;
	}

	/**
	 * Returns  AVD Manager
	 * @return AvdManager object
	 */
	public AvdManager getAvdManager()
	{
		String avdFolder = null;
		AvdManager avdManager = null;
        try {
            avdFolder = AndroidLocation.getAvdFolder();
            avdManager = AvdManager.getInstance(handler, new File(avdFolder), sdkLogger);
        } catch (AndroidLocation.AndroidLocationException e) {
        	sdkLogger.error(e, "Error obtaining AVD Manager");
        }
        return avdManager;
	}

	/**
	 * Returns packages cached by Repository Manager
	 * @return RepositoryPackages object
	 */
	public RepositoryPackages getPackages() {
		return repoManager.getPackages();
	}

	/**
	 * Returns object to perform native file operations
	 * @return FileOp object
	 */
	public FileOp getFileOp() {
		return handler.getFileOp();
	}

	/**
	 * Returns the path to the local repository root
	 * @return File object
	 */
	public File getLocalPath() {
		return repoManager.getLocalPath();
	}

	/**
	 * Returns SDK location
	 * @return File object
	 */
	public File getLocation() {
		File location = handler.getLocation();
		if (location == null)
			return new File("");
		return location;
	}

	/**
	 * Returns a {@link Map} from {@code path} (the unique ID of a package) to * {@link RemotePackage}.
	 * @return Map object
	 */
	public Map<String, RemotePackage> getRemotePackages() {
		return getPackages().getRemotePackages();
	}

	/**
	 * Returns a map of {@code path} (the unique ID of a package) to {@link LocalPackage}, for all packages currently installed.
	 * @return Map object
	 */
	public Map<String, LocalPackage> getLocalPackages() {
		return getPackages().getLocalPackages();
	}

	/**
	 * Returns the currently registered {@link RepositorySourceProvider}s
	 * @return RepositorySourceProvider set
	 */
	public Set<RepositorySourceProvider> getSourceProviders() {
		return repoManager.getSourceProviders();
	}

	/**
	 * Returns Device Manager
	 * @return DeviceManager object
	 */
	public DeviceManager getDeviceManager() {
		return deviceManager;
	}

	/**
	 * Returns flag set true if insternal logger has warning
	 * @return boolean
	 */
	public boolean hasWarning() {
		return hasWarning.get();
	}

	/**
	 * Returns flag set true if insternal logger has error
	 * @return boolean
	 */
	public boolean hasError() {
		return hasError.get();
	}

	/**
	 * Returns internal logger messages
	 * @return String list
	 */
	public ArrayList<String> getLogMessages() {
		return logMessages;
	}

	/**
	 * Sets progress indicator
	 * @param sdkProgressIndicator The Progress Indicator
	 */
	public void setSdkProgressIndicator(ProgressIndicator sdkProgressIndicator) {
		this.sdkProgressIndicator = sdkProgressIndicator;
	}

	/**
	 * Set logger
	 * @param sdkLogger Android logger
	 */
	public void setSdkLogger(ILogger sdkLogger) {
		if (!logMessages.isEmpty()) {
			if (hasError.get())
				sdkLogger.error(null, "Error occurred on start. Details follow");
			else if (hasWarning.get())
				sdkLogger.warning("Warning occurred on start. Details follow");
			else
				sdkLogger.error(null, "Information collected on start. Details follow");
			hasWarning.set(false);
			hasError.set(false);
			for (String message: logMessages) {
				sdkLogger.info(message);
			}
			logMessages.clear();
		}
		this.sdkLogger = sdkLogger;
		sdkHelper.setLogger(sdkLogger);
	}

	/**
	 * Returns progress indicator
	 * @return ProgressIndicator - will be adapter unless {@link #setSdkProgressIndicator(ProgressIndicator)} is called prior
	 */
	public ProgressIndicator getProgressIndicator() {
		if (sdkProgressIndicator != null)
			return sdkProgressIndicator;
		return new ProgressIndicatorAdapter()
		{
			ILogger logger = getSdkLog();
		    @Override
		    public void logWarning(@NonNull String s, @Nullable Throwable e) {
		    	if (s != null)
		    		logger.warning(s);
		    	if (e != null)
		    		logger.warning(e.getMessage());
		    }

		    @Override
		    public void logError(@NonNull String s, @Nullable Throwable e) {
		    	logger.error(e, s);
		    }

		    @Override
		    public void logInfo(@NonNull String s) {
		    	logger.info(s);
		    }
		};
	}

	/**
	 * Returns logger
	 * @return ILogger object
	 */
	public ILogger getSdkLog() {
		return sdkLogger;
	}

	/**
	 * Set SDK location
	 * @param sdkLocation SDK location as File object
	 */
	public void setLocation(File sdkLocation) {
		// This change needs to be monitored so external AndroidSdkHandler consumers can re-sync
		sdkLocationChanged = true;
		AndroidSdkHandler.resetInstance(sdkLocation);
		handler = AndroidSdkHandler.getInstance(sdkLocation);
		repoManager = handler.getSdkManager(sdkProgressIndicator);
	}

	/**
	 * Reload local packages. To be called after package install/delete.
	 */
	public void reloadLocalPackages() {
		File sdkLocation = handler.getLocation();
		// The handler needs to be replaced to force all managers to reload
		AndroidSdkHandler.resetInstance(sdkLocation);
		handler = AndroidSdkHandler.getInstance(sdkLocation);
		repoManager = handler.getSdkManager(sdkProgressIndicator);
	}

	/**
	 * Initial logger to accumulate information until {@link #setSdkLogger(ILogger)} is called
	 * @return
	 */
	private ILogger loggerInstance() {
		hasWarning.set(false);
		hasError.set(false);
		logMessages.clear();
		return new ILogger() {
            @Override
            public void error(@Nullable Throwable throwable, @Nullable String errorFormat,
                    Object... arg) {
            	hasError.set(true);
                if (errorFormat != null) {
                    logMessages.add(String.format("Error: " + errorFormat, arg));
                }

                if (throwable != null) {
                    logMessages.add(throwable.getMessage());
                }
            }

            @Override
            public void warning(@NonNull String warningFormat, Object... arg) {
            	hasWarning.set(true);
                logMessages.add(String.format("Warning: " + warningFormat, arg));
            }

            @Override
            public void info(@NonNull String msgFormat, Object... arg) {
                logMessages.add(String.format(msgFormat, arg));
            }

            @Override
            public void verbose(@NonNull String msgFormat, Object... arg) {
                info(msgFormat, arg);
            }
        };

	}

}
