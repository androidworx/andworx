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

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.andmore.sdktool.preferences.AndroidPreferenceStore;

import com.android.repository.api.Channel;
import com.android.repository.api.SettingsController;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileSystemFileOp;
import com.android.utils.ILogger;

/**
 *  Settings used by the repository framework.
 */
public class Settings implements SettingsController {
	
	public static final String FORCE_HTTP_KEY = "sdkman.force.http";
	public static final String CHANNEL_KEY = "sdkman.channel";
	private static final String SETTINGS_FILENAME = "androidtool.cfg";

	/** Channel 0 (Stable), 1 (Beta), 2 (Dev), and 3 (Canary) */
    private int channel = 0;
    /** Wraps some common {@link File} operations on files and folders */
    private final FileOp fileOp;
    /** Android preference store locates Android Home and provides default if this fails */
    private final AndroidPreferenceStore prefStore;
    /** In-memory copy of settings */
    private final Properties settings;
    /** Logger required to record possible preference store exceptions  */
    private ILogger logger;

    /**
     * Construct Settings object
     * @param logger Logger required to record possible preference store exceptions
     */
    public Settings(ILogger logger)
    {
    	this.logger = logger;
        settings = new Properties();
    	fileOp = (FileSystemFileOp)FileOpUtils.create();
    	// Sets Android home
    	prefStore = new AndroidPreferenceStore(logger);
    }

    /**
     * Returns flag set true if previews enabled. Logic: True if channel != stable.
     * @return boolean
     */
	public boolean getEnablePreviews() {
		return channel > 0;
	}

	/**
	 * getForceHttp
	 */
	@Override
	public boolean getForceHttp() {
		if (settings.isEmpty())
			return false;
        return Boolean.parseBoolean(settings.getProperty(FORCE_HTTP_KEY, "false"));
	}

	/**
	 * setForceHttp
	 */
	@Override
	public void setForceHttp(boolean force) {
        settings.setProperty(FORCE_HTTP_KEY, Boolean.toString(force));
	}

	/**
	 * getChannel
	 */
    @Override
    public Channel getChannel() {
		if (!settings.isEmpty())
			channel = Integer.parseInt(settings.getProperty(CHANNEL_KEY, "0"));
        return Channel.create(channel);
    }

    /**
     * Set channnel
     * @param id Channel number 0 - 3
     */
    public void setChannel(int id) {
    	channel = id;
    	settings.setProperty(CHANNEL_KEY, Integer.toString(channel));
    }
 
    /**
     * Load settings from disk
     * @return flag set true if load completed successfully
     */
	public boolean initialize() {
		try {
			loadSettings();
		} catch (IOException e) {
			logger.error(e, "Error initializing SDK Manager settings");
			return false;
		}
		return true;
	}

	/**
	 * Returns copy of this object
	 * @return
	 */
	public Settings copy() {
		Settings copy = new Settings(logger);
		// Load is not expected to fail. Copy will contain defaults if it does.
		try {
			copy.loadSettings();
		} catch (IOException e) {
		}
		return copy;
	}
	
	/**
	 * Load settings from disk implementation creates properties file if it does not exist
	 * @throws IOException
	 */
    private void loadSettings() throws IOException
    {
        File file = new File(prefStore.getAndroidHome(), SETTINGS_FILENAME);
        if (!file.exists())
        	try {
        		file.createNewFile();
            } catch (SecurityException e) {
            	throw new IOException(e.getMessage(), e);
            }
        settings.clear();
        settings.load(fileOp.newFileInputStream(file));
        // Discontinued
        //setShowUpdateOnly(getShowUpdateOnly());
        //setSetting("sdkman.ask.adb.restart", getAskBeforeAdbRestart());
        //setSetting("sdkman.use.dl.cache", getUseDownloadCache());
        //setSetting("sdkman.enable.previews2", getEnablePreviews());
    }


}
