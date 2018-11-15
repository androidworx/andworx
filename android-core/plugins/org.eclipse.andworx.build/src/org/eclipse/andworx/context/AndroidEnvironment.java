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
package org.eclipse.andworx.context;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.sdk.TargetLoadStatusMonitor;

import com.android.annotations.Nullable;
import com.android.builder.model.SigningConfig;
import com.android.builder.signing.DefaultSigningConfig;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.repository.api.RepoManager;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;

/**
 * References a single Android SDK installation and resources in the Android environment
 */
public class AndroidEnvironment {
    private final static String ANDWORX_HOME_PATH = "andworx";
	private static final String ANDWORX_HOME_ERROR = "Error creating Andworx home directory %s";
	private static final String REPOSITORY_FOLDER = "libraries";
	private static final String DEFAULT_ANDROID_HOME = System.getProperty("user.dir") + File.separator + AndroidLocation.FOLDER_DOT_ANDROID;

	private static class FailedTargetLoadStatus implements TargetLoadStatusMonitor {

		@Override
		public LoadStatus getLoadStatus(String hashString) {
			return LoadStatus.FAILED;
		}
		
	}
	
	/** Location of the expanded AARs stored in a Maven repository structure */ 
	protected File repositoryLocation;
	/** Android's own home location */
	protected File androidHome;
	private final ILogger logger;
	protected TargetLoadStatusMonitor targetLoadStatusMonitor;

	public AndroidEnvironment(ILogger logger) {
		this.logger = logger;
		targetLoadStatusMonitor = new FailedTargetLoadStatus();
	}
	
	/**
	 * Returns flag set true if Android SDK is available
	 * @return boolean
	 */
	public boolean isValid() {
		return false;
	}

	/**
	 * Returns the available Android targets (both Platform and Add-ons
	 * @return Android target collection
	 */
	public Collection<IAndroidTarget> getAndroidTargets() { 
		return Collections.emptyList();
    }

	/**
	 * Returns the location of the libraries repository within Android home
	 * @return File object
	 */
	public File getRepositoryLocation() {
		if (repositoryLocation == null)
			repositoryLocation = new File(getAndworxHome(), REPOSITORY_FOLDER);
		return repositoryLocation;
	}

	/**
	 * Returns Android SDK interface to {@link RepoManager}
	 * @return AndroidSdkHandler object or null if not available
	 */
	@Nullable
	public  AndroidSdkHandler getAndroidSdkHandler() {
		return null;
	}
	
	/**
	 * Returns closest match Target Platform to given version
	 * @param targetHash Target platform version specified as a hash string
	 * @return IAndroidTarget object or null if none available
	 */
	@Nullable
	public IAndroidTarget getAvailableTarget(String targetHash) {
		return null;
	}
	
	/**
	 * Returns Android's own home location. 
	 * @return File object
	 */
	public File getAndroidHome() {
		if (androidHome == null) {
			try {
        	/* getFolder()
		     * Returns the folder used to store android related files.
		     * If the folder is not created yet, it will be created.
		     * @return an OS specific path, terminated by a separator.
        	 */
			String androidHomeDir = AndroidLocation.getFolder();
            androidHome = new File(androidHomeDir);
            if (!androidHome.exists()) {
            	if (!androidHome.mkdirs())
            		throw new IOException(String.format("Cannot create path %s", androidHome.getAbsolutePath()));
            } else if (!androidHome.isDirectory())
        		throw new IOException(String.format("Path %s is not a directory", androidHome.getAbsolutePath()));
	        } catch (AndroidLocationException | IOException | SecurityException e) {
	        	logger.error(e, "Error finding Android home");
	        	// Apply default
	        	androidHome = new File(DEFAULT_ANDROID_HOME);
	        }
		}
		return androidHome;
	}

	/**
	 * Returns Andworx's own home location inside Android home
	 * @return File object
	 */
	public File getAndworxHome() {
    	File androidHomeDir = getAndroidHome();
        File andworxHomePath = new File(androidHomeDir, ANDWORX_HOME_PATH);
    	if (!andworxHomePath.exists()) {
    		try {
    			andworxHomePath.mkdirs();
    		} catch (SecurityException t) {
    			logger.error(t, ANDWORX_HOME_ERROR, andworxHomePath);
    		}
    	}
    	if (!andworxHomePath.exists()) 
    		throw new AndworxException(String.format(ANDWORX_HOME_ERROR, andworxHomePath));
    	return andworxHomePath;
    }
	
	/**
	 * Return Signing Configuration
	 * @return SigningConfig object
	 */
	public SigningConfig getDefaultDebugSigningConfig() {
		File keystoreFile = new File(getAndroidHome(), "debug.keystore");
		if (!keystoreFile.exists())
			try {
				KeystoreHelper.createDebugStore(
					null, 
					keystoreFile, 
					DefaultSigningConfig.DEFAULT_PASSWORD, 
					DefaultSigningConfig.DEFAULT_PASSWORD, 
					DefaultSigningConfig.DEFAULT_ALIAS, 
					logger);
			} catch (KeytoolException e) {
				throw new AndworxException("Error creating debug keystore " + keystoreFile.toString(), e);
			}
		return DefaultSigningConfig.debugSigningConfig(keystoreFile);
	}

	/**
	 * Returns monitor which always return FAILED load status. Replace targetLoadStatusMonitor to return actual status.
	 * @return TargetLoadStatusMonitor
	 */
	public TargetLoadStatusMonitor getTargetLoadStatusMonitor() {
		return targetLoadStatusMonitor;
	}
}
