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
package org.eclipse.andworx.file;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.eclipse.andworx.log.SdkLogger;

import com.android.builder.utils.FileCache;
import com.google.common.base.Preconditions;

public class CacheManager {

	private static SdkLogger logger = SdkLogger.getLogger(CacheManager.class.getName());
	
    /** A cache for already-created files/directories */
	private FileCache userFileCache;
	/** Maps cached file to file name */
	private Map<String, CachedFile> cachedFileMap;
	/** Plugin version to apply to file cache */
	private String pluginVersion;

	public CacheManager(FileCache userFileCache, String pluginVersion) {
		this.userFileCache = userFileCache;
		this.pluginVersion = pluginVersion;
		cachedFileMap = new HashMap<>();
	}

	/**
	 * Add file to Android SDK build utils cache 
	 * @param cachedFile Reference to file to be placed in cache
	 * @throws IOException
	 */
	public void addFile(CachedFile cachedFile) throws IOException {
    	String filename = cachedFile.getFileResource().getFileName(); 
    	synchronized(cachedFileMap) {
	        cachedFileMap.put(filename, cachedFile);
	        if (!cachedFile.isInitialized()) {
	            initializeFile(cachedFile);
	        }
    	}
    }

	/**
	 * Returns absolute path to given filename
	 * @param filename Name of file in cache
	 * @return File object
	 */
    public File getFile(String filename) {
    	File file = null;
    	synchronized(cachedFileMap) {
	    	CachedFile cachedFile = cachedFileMap.get(filename);
	    	if (cachedFile != null) {
	    		file = cachedFile.getPath().toFile();
	    	}
    	}
    	return file;
    }
  
    /**
     * Place file in given cache
	 * @param cachedFile Reference to file to be placed in cache
     * @throws IOException
     */
	private void initializeFile(CachedFile cachedFile) throws IOException {
		FileResource resource = cachedFile.getFileResource();
        URL url = resource.asUrl();
        Preconditions.checkNotNull(url);

        Path path = null;
        if (userFileCache != null) {
            try {
            	path = cachedFile.initialize(userFileCache, pluginVersion);
            } catch (IOException | ExecutionException e) {
                logger.error(e, "Unable to cache " + resource.getFileName() + ". Extracting to temp dir.");
            }
        }
        cachedFile.update(path);
	}

}
