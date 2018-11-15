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
import java.io.IOException;
import java.util.function.Supplier;

import  org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.google.common.base.Throwables;

/**
 * Implements AndroidSdkPreferences interface which conceals details of how the Android SDK location is persisted.
 */
public class AndroidSdkHelper implements AndroidSdkPreferences {
	/** Key to Android SDK location in persistence store */
    public final static String PREFS_SDK_DIR = "org.eclipse.andmore.sdk";
    /** Key to the last recorded SDK location stored in Android home */
	private final static String LAST_SDK_PATH = "lastSdkPath";
	private static final String SDK_LOC_SAVE_ERROR = "Error saving SDK location";

	private static SdkLogger logger = SdkLogger.getLogger(AndroidSdkHelper.class.getName());
	
    /** Flag set true if SDK location has been changed in persistent storage */
    private volatile boolean isSdkLocationChanged;
    /** Preference store for persisting SDK location, referenced by function call. */
    private final Supplier<ScopedPreferenceStore> preferenceStore;

    /** 
     * Construct AndroidSdkHelper object
     * @param preferenceStore Supplier of preference to use
     */
    public AndroidSdkHelper(Supplier<ScopedPreferenceStore> preferenceStore) {
    	this.preferenceStore = preferenceStore;
    	isSdkLocationChanged = false;
    }
    
	/**
	 * Returns SDK location recorded in Android home, if it exists and is valid
	 * @return File object or null if not available
	 */
	@Nullable
	@Override
	public File getLastSdkPath() {
		File sdkPath = null;
		PreferenceStore ddmsStore = getDdmsPreferenceStore();
        if (ddmsStore != null) {
            String lastSdkValue = ddmsStore.getString(LAST_SDK_PATH);
            if (lastSdkValue != null) {
            	File lastSdkPath = new File(lastSdkValue);
            	if (lastSdkPath.exists() && lastSdkPath.isDirectory())
            		sdkPath = lastSdkPath;
            }
        }
		return sdkPath;
	}

	/**
	 * Returns Android SDK location obtained from either workspace
	 * @return File object or null if not available
	 */
	@Nullable
	@Override
	public File getSdkLocation() {
		File sdkPath = null;
        String sdkPathValue = getSdkLocationValue();
        if (!sdkPathValue.isEmpty()) {
           	sdkPath = new File(sdkPathValue);
           	if (!sdkPath.exists() || !sdkPath.isDirectory())
           		sdkPath = null;
        }
		return sdkPath;
	}

	/**
	 * Set SDK location in preference store
	 * @param location SDK location
	 */
	@Override
	public void setSdkLocation(File location) {
        String osSdkLocation = location != null ? location.getAbsolutePath() : null;

        // Also store this location in the Android home directory
        // such that we can support using multiple workspaces
        if (osSdkLocation != null && osSdkLocation.length() > 0) {
			PreferenceStore ddmsStore = getDdmsPreferenceStore();
		    if (ddmsStore != null) {
	            try {
	            	ddmsStore.setValue(LAST_SDK_PATH, osSdkLocation);
	            	ddmsStore.save();
	            }
	            catch (IOException ioe) {
	                logger.warning("Failed saving DDMS prefs file\n%s" + Throwables.getStackTraceAsString(ioe));
	            }
		    }
		    String currentLocation = getSdkLocationValue();
		    isSdkLocationChanged = ((currentLocation != null) && !currentLocation.equals(osSdkLocation));
	        preferenceStore.get().setValue(PREFS_SDK_DIR, osSdkLocation);
        }
	}

	/**
	 * Returns flag set true if SDK is set in preferences
	 * @return boolean
	 */
	@Override
	public boolean isSdkSpecified() {
        String osSdkFolder = getSdkLocationValue();
        return (osSdkFolder != null && !osSdkFolder.isEmpty());
	}

	/**
     * Adds a property change listener to this preference store to be notified when the SDK location changes
     * @param listener A property change listener
	 */
	@Override
	public void addPropertyChangeListener(IPropertyChangeListener listener) {
		preferenceStore.get().addPropertyChangeListener(new IPropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (isSdkLocationChanged && event.getProperty().equals(PREFS_SDK_DIR))
					listener.propertyChange(event);
			}});
	}

	/**
	 * Returns SDK location value from preference store
	 * @return SDK location
	 */
	@Override
	public String getSdkLocationValue() {
		return preferenceStore.get().getString(PREFS_SDK_DIR);
	}

	/**
	 * Write preference store to disk
	 * @return flag set true if operation completed successfully
	 */
	@Override
	public boolean save() {
		ScopedPreferenceStore store = preferenceStore.get();
        synchronized (store) {
            try {
            	store.save();
            	return true;
            }
            catch (IOException e) {
            	logger.error(e, SDK_LOC_SAVE_ERROR);
           }
        }
        return false;
	}
	
	/**
	 * Returns DDMS preference store located in Android home
	 * @return PreferenceStore object or null if not found or otherwise unavailable
	 */
	private PreferenceStore getDdmsPreferenceStore() {
		PreferenceStore ddmsStore = null;
        File ddmsCfgLocation = null;
        try {
            String homeDir = AndroidLocation.getFolder();
            ddmsCfgLocation = new File(homeDir + "ddms.cfg");
            if (ddmsCfgLocation.exists()) {
                ddmsStore = new PreferenceStore(ddmsCfgLocation.getAbsolutePath());
                ddmsStore.load();
                return ddmsStore;
            }
        } catch (AndroidLocationException | IOException e1) {
        	logger.warning("Error saving to preference store:\n %s", Throwables.getStackTraceAsString(e1));
        }
        return ddmsStore;
	}
}
