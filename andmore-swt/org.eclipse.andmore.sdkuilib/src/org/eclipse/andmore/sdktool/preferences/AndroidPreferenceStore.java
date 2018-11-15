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

package org.eclipse.andmore.sdktool.preferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.preference.PreferenceStore;

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.utils.ILogger;

/**
 * Preferences residing in Android home and thus shared by all Eclipse workspaces.
 * The Android home location would be normally defined as folder ".android" under the user's home
 * directory. The environment variables "ANDROROID_SDK_HOME" and "HOME" can also define where ".android"
 * is located.
 * <p/>
 * Each SDK installation has it's own preference file (android.cfg) in a folder under ".eclipse" in
 * the Android home location. There is also a legacy "lastSdkPath" property is stored in ddms.cfg which may 
 * be discontinued at some future point.
 * @author Andrew Bowley
 *
 * 20-12-2017
 */
public class AndroidPreferenceStore {
	private final static String BLANK = "";
    private final static String SDK_KEYS = "sdkKeys";      
    private final static String LAST_SDK_PATH = "lastSdkPath";      
    private final static String  DDMS_CFG_FILE = "ddms.cfg";
    private final static String ANDMORE_HOME_PATH = ".eclipse";
    private final static String ANDMORE_CFG_FILE = "android.cfg";
    private final static String ANDMORE_CFG_PATH = ANDMORE_HOME_PATH + "/" + ANDMORE_CFG_FILE;
	private static final String SDK_LOCATION = "sdkLocation";
	private static final String SDK_NAME = "sdkName";
	private static final String SDK_HIDDEN = "sdkHidden";
	private static final String SDK_KEY = "sdkKey";
	private static final String NO_ANDROID_HOME = "Android home for Preference store %s not found";
	private static final String NOT_FILE = "%s is not a Preference store file";
	private static final String CANNOT_CREATE_FILE ="Error creating Preference store file %s";
	private static final String CANNOT_CREATE_DIRECTORY ="Error creating Preference store directory %s";

    /** Android master preference store singleton holds keys to access individual SDK preferences */
    private static PreferenceStore preferenceStore;
    /** Android home directory located using Android library */
    private static String androidHomeDir;
    /** Logger required to record possible preference store exceptions  */
    private ILogger logger;

    /**
     * Construct an AndroidPreferenceStore object
     * @param logger Logger
     */
    public AndroidPreferenceStore(ILogger logger) {
    	this.logger = logger;
    	// Master preference store singleton used as lock for thread safety
    	getPreferenceStore(logger);
	}

    /**
     * Returns all Android SDK specifications. This always invoives multiple file operations as there is no caching.
     * @return AndroidSdk list
     */
    public List<AndroidSdk> getAndroidSdkList() {
    	List<AndroidSdk> androidSdkList = new ArrayList<>();
    	// Use the SDK keys from the master preferences to collect all stored Android SDK specifications
    	Set<String> keys = getSdkKeys();
    	for (String key: keys) {
    		AndroidSdk androidSdk = getAndroidSdk(key);
    		if (androidSdk != null)
    			androidSdkList.add(androidSdk);
    	}	
    	// Sort list for consistency when viewing list
    	Collections.sort(androidSdkList,  new Comparator<AndroidSdk>(){

			@Override
			public int compare(AndroidSdk sdk1, AndroidSdk sdk2) {
				// Sort on name and then on location
				if (!sdk1.getName().isEmpty() && !sdk2.getName().isEmpty())
					return sdk1.getName().compareToIgnoreCase(sdk2.getName());
				return sdk1.getSdkLocation().compareTo(sdk2.getSdkLocation());
			}});
    	return androidSdkList;
    }

    /**
     * Returns legacy last SDK path
     * @return absolute path to last reported Android SDK or default value if it does not exist
     */
    public String getLastSdkPath() {
    	return preferenceStore.getString(LAST_SDK_PATH);
    }

	public String getAndroidHome() {
		return androidHomeDir;
	}

    /**
     * Save given Android SDK specification
     * @param androidSdk The Android SDK specification
     */
    public void saveAndroidSdk(AndroidSdk androidSdk) {
    	// Need to reference existing SDKs to check for duplication
    	List<AndroidSdk> androidSdkList = new ArrayList<>();
    	Set<String> keys = getSdkKeys();
    	for (String key: keys) {
    		AndroidSdk sdk = getAndroidSdk(key);
    		if (sdk != null)
    			androidSdkList.add(sdk);
    	}	
    	String key = androidSdk.getKey();
    	boolean isNew = (key == null) || key.isEmpty();
    	AndroidSdk matchSdk = null;
    	if (isNew) {
    		// Check for existing SDK match on location
    		for (AndroidSdk sdk: androidSdkList) {
    			if (sdk.getSdkLocation().equals(androidSdk.getSdkLocation())) {
    				matchSdk = sdk;
    				break;
    			}
    		}
    	}
    	if (matchSdk != null) {
			// Update store only if the name or hidden flag has changed
    		boolean hasChanged = false;
			if (!androidSdk.getName().isEmpty() && !androidSdk.getName().equals(matchSdk.getName())) {
				matchSdk.setName(androidSdk.getName());
				hasChanged = true;
			} else if (androidSdk.isHidden() != matchSdk.isHidden()) {
				matchSdk.setHidden(androidSdk.isHidden());
				hasChanged = true;
			}
			if (hasChanged) {
		    	PreferenceStore sdkStore = new PreferenceStore(getKeyPath(matchSdk.getKey()));
		    	savePreferences(sdkStore);
			} 
			return;
    	}
 		if (isNew) {
			// A new SDK requires a key to be generated
			key = androidSdk.setKey();
			keys.add(key);
		}
		// Use key as folder name for new preferece store
    	File keyDir = new File(ANDMORE_HOME_PATH, key);
		File keyPath = new File(androidHomeDir, keyDir.toString());
		if (isNew) {
			try {
				if (!keyPath.mkdirs()) {
					logger.error(null, "Failed to create directory %s", keyPath.toString());
					return;
				}
			} catch(SecurityException e) {
				logger.error(e, "error creating directory %s", keyPath.toString());
				return;
			}

		}
		keyPath = new File(keyPath, ANDMORE_CFG_FILE);
     	PreferenceStore sdkStore = new PreferenceStore(keyPath.toString());
		boolean success = false;
        synchronized (AndroidPreferenceStore.class) {
        	sdkStore.setValue(SDK_NAME, androidSdk.getName());
        	sdkStore.setValue(SDK_LOCATION, androidSdk.getSdkLocation().toString());
        	sdkStore.setValue(SDK_HIDDEN, androidSdk.isHidden());
        	sdkStore.setValue(SDK_KEY, androidSdk.getKey());
        	success = savePreferences(sdkStore);
        }
        if (isNew && success)
        	setSdkkeys(keys);
   }
    
    /**
     * Save the prefs to the config file.
     * @return flag set true if save completed successfully
     */
    public boolean save() {
    	return savePreferences(getPreferenceStore(logger));
    }

    /**
     * Returns Android SDK specification for given key
     * @param key 8-character generated SDK key
     * @return AndroidSdk object or null if error occurs
     */
    private AndroidSdk getAndroidSdk(String key) {
    	PreferenceStore sdkStore = new PreferenceStore(getKeyPath(key));
    	AndroidSdk androidSdk = null;
        try {
        	sdkStore.load();
			//Android SDK root location
        	File sdkLocation = getSdkLocation(sdkStore);
        	// Name is optional
        	String name = getSdkName(sdkStore);
        	androidSdk = new AndroidSdk(sdkLocation, name, getSdkKey(sdkStore));
        	androidSdk.setHidden(getSdkHidden(sdkStore));
        	return androidSdk;
        } catch (IOException e) {
        	logger.error(e, "Error loading Android SDK key " + key);
        }
 		return androidSdk;
	}

    private String getKeyPath(String key) {
    	StringBuilder builder = new StringBuilder(androidHomeDir);
    	builder.append(File.separator)
    	       .append(ANDMORE_HOME_PATH)
    	       .append(File.separator)
    	       .append(key)
    	       .append(File.separator)
    	       .append(ANDMORE_CFG_FILE);
		return builder.toString();
	}

	/**
     * Returns SDK location from given preference store
     * @param sdkStore The preference store
     * @return File object
     */
    private File getSdkLocation(PreferenceStore sdkStore) {
    	String sdkLocation;
        synchronized (AndroidPreferenceStore.class) {
        	sdkLocation = sdkStore.getString(SDK_LOCATION);
        }
    	return new File(sdkLocation);
	}

    /**
     * Returns SDK name from given preference store
     * @param sdkStore The preference store
     * @return String
     */
    private String getSdkName(PreferenceStore sdkStore) {
    	String sdkName;
        synchronized (AndroidPreferenceStore.class) {
        	sdkName = sdkStore.getString(SDK_NAME);
        }
    	return sdkName;
	}

    /**
     * Returns SDK hidden flage from given preference store
     * @param sdkStore The preference store
     * @return boolean
     */
    private boolean getSdkHidden(PreferenceStore sdkStore) {
    	boolean sdkHidden;
        synchronized (AndroidPreferenceStore.class) {
        	sdkHidden = sdkStore.getBoolean(SDK_HIDDEN);
        }
    	return sdkHidden;
	}

    /**
     * Returns SDK key from given preference store
     * @param sdkStore The preference store
     * @return String
     */
    private String getSdkKey(PreferenceStore sdkStore) {
    	String sdkKey;
        synchronized (AndroidPreferenceStore.class) {
        	sdkKey = sdkStore.getString(SDK_KEY);
        }
    	return sdkKey;
	}

    /**
     * Returns preference store for given path relative to Android home location
     * @param path The path relative to Andriod home location
     * @return PreferenceStore object
     */
	private static PreferenceStore getPreferenceStore(String path, ILogger logger) {
		PreferenceStore preferenceStore = null;
        if (androidHomeDir != null) {
    		File preferenceStoreFile = new File(androidHomeDir + path);
    		File directoryPath = null;
    		try {
    			directoryPath = preferenceStoreFile.getParentFile();
    			if (!directoryPath.exists()) {
    				if (!directoryPath.mkdirs()) {
    	   				logger.error(null, CANNOT_CREATE_DIRECTORY, directoryPath.getPath());
    	   				return null;
    				}
    			}
    			if (!preferenceStoreFile.exists()) {
    				if (!preferenceStoreFile.createNewFile()) {
    		        	logger.warning(CANNOT_CREATE_FILE, preferenceStoreFile.getPath());
    				    return null;
    				}
    			}
    			if (preferenceStoreFile.isFile())
    				preferenceStore = new PreferenceStore(preferenceStoreFile.getPath());
    			else
    	        	logger.warning(NOT_FILE, preferenceStoreFile.getPath());
    		} catch(IOException e) {
    			logger.error(e, CANNOT_CREATE_FILE, preferenceStoreFile.getPath());
    		} catch (SecurityException e) {
    			logger.error(e, CANNOT_CREATE_DIRECTORY, directoryPath.getPath());
    		}
        } else {
        	logger.warning(NO_ANDROID_HOME, path);
        }
        return preferenceStore;
    }
    
    /**
     * Returns set of SDK keys in master preference store
     * @return String
     */
    private Set<String> getSdkKeys() {
    	Set<String> value = new HashSet<>();
        String commaDelimited = BLANK;
        synchronized (AndroidPreferenceStore.class) {
            commaDelimited = preferenceStore.getString(SDK_KEYS);
        }
        if (!commaDelimited.isEmpty()) {
        	String[] keys = commaDelimited.split(",");
    		for (String key: keys)
    			value.add(key);
        }
        return value;
    }

    /**
     * Sets the SDK keys in the master preference store
     * @param sdkKeys SDK keys set
     */
    private void setSdkkeys(Set<String> sdkKeys) {
    	StringBuilder builder = new StringBuilder();
    	boolean firstTime = true;
    	for (String key: sdkKeys) {
    		if (firstTime)
    			firstTime = false;
    	    else
    		    builder.append(",");
    		builder.append(key);
    	}
        synchronized (AndroidPreferenceStore.class) {
        	preferenceStore.setValue(SDK_KEYS, builder.toString());
        }
    	savePreferences(preferenceStore);
    }

    /**
     * Returns property stored in ddms.cfg file. This is for legacy support only.
     * @param homeDir Android home location
     * @return last reported SDK location or null if not available
     */
	private static String getDdmsLastSdkPath(ILogger logger) {
		
        PreferenceStore ddmsPrefStore = getPreferenceStore(DDMS_CFG_FILE, logger);
        if (ddmsPrefStore != null) {
	        try {
	        	ddmsPrefStore.load();
	        	return ddmsPrefStore.getString(LAST_SDK_PATH);
	        } catch (IOException e) {
	        	logger.error(e, "Error loading DDMS Preferences");
	        }
        }
		return null;
	}

	/**
	 * Save SDK preference store
	 * @param sdkStore The SDK preference store
	 * @return flag set true if save completed successfully
	 */
	private boolean savePreferences(PreferenceStore sdkStore) {
		// Do not store value obtained from ddms.cfg
		sdkStore.setToDefault(LAST_SDK_PATH);
        synchronized (AndroidPreferenceStore.class) {
            try {
            	sdkStore.save();
            	return true;
            }
            catch (IOException e) {
            	logger.error(e, "Error saving Android Preferences");
            }
        }
        return false;
	}
	
	/**
     * Returns the Android master {@link PreferenceStore} which contains all SDK specification keys.
     */
    private static PreferenceStore getPreferenceStore(ILogger logger) {
        if (preferenceStore == null) {
	        synchronized (AndroidPreferenceStore.class) {
	            if (preferenceStore == null) {
	                try {
	                	/* getFolder()
	        		     * Returns the folder used to store android related files.
	        		     * If the folder is not created yet, it will be created.
	        		     * @return an OS specific path, terminated by a separator.
	                	 */
	                    androidHomeDir = AndroidLocation.getFolder();
	                    File androidHomeFile = new File(androidHomeDir);
	                    if (!androidHomeFile.exists()) {
	                    	if (!androidHomeFile.mkdirs())
	                    		throw new IOException(String.format("Cannot create path %s", androidHomeFile.getAbsolutePath()));
	                    } else if (!androidHomeFile.isDirectory())
                    		throw new IOException(String.format("Path %s is not a directory", androidHomeFile.getAbsolutePath()));
	                } catch (AndroidLocationException | IOException | SecurityException e) {
	                	logger.error(e, "Error finding Android home");
	                	// Use default
	                	androidHomeDir = System.getProperty("user.dir") + File.separator + AndroidLocation.FOLDER_DOT_ANDROID;
	                	File androidHomeFile = new File(androidHomeDir);
	                	if (!androidHomeFile.exists()) {
		                	// Following may fail or cause a SecurityException, which cannot be handled in this context
	                		// Subsequent Preference store operations will always fail if this operation fails
	                		try {
	                			androidHomeFile.mkdir(); 
	                		} catch (SecurityException t) {
	                			logger.error(t, "Error creating Android home directory %s", androidHomeDir);
	                		}
	                	}
	                }
	                if (androidHomeDir != null) {
	                	String lastSdkPath = null;
		            	PreferenceStore androidPrefStore = getPreferenceStore(ANDMORE_CFG_PATH, logger);
		            	if (androidPrefStore != null) {
			                lastSdkPath = getDdmsLastSdkPath(logger);
		                	File configPath =  new File(androidHomeDir + ANDMORE_CFG_PATH);
		                	if (configPath.exists()) {
				                try {
				                	androidPrefStore.load();
				                	preferenceStore = androidPrefStore;
				                } catch (IOException e) {
				                	logger.error(e, "Error loading Android Preferences");
			                    }
		                	} else
			                	preferenceStore = androidPrefStore;
		            	}
		                if (preferenceStore == null)
		                	preferenceStore.setValue(LAST_SDK_PATH, lastSdkPath != null ? lastSdkPath : "");
	                }
	                if (preferenceStore == null) {
	                	preferenceStore = new PreferenceStore();
	                }
	            }
                return preferenceStore;
	        }
        }
    	return preferenceStore;
    }

}
