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
package org.eclipse.andworx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.log.SdkLogger;

import dagger.Module;
import dagger.Provides;

/**
 * Makes bundle file available to application by
 * copying it to location controlled by file manager
 */
@Module
public class BundleFileModule {

	private static SdkLogger logger = SdkLogger.getLogger(BundleFileModule.class.getName());
	
	private final String filePath;

	/**
	 * Construct BundleFileModule object
	 * @param pluginBundle Plugin bundle
	 * @param filePath File path
	 */
	public BundleFileModule(String filePath) {
		this.filePath = filePath;
	}
	
    /**
     * Returns bundle file specified by filePath field. 
     * The file manager takes care of extracting the bundle file and caching it on the file system.
     * @param fileManager FileManager object
     * @return File object or null if an error occurs
     */
	@Provides
	File provideFile(FileManager fileManager) {
    	File bundleFile = fileManager.getTargetPath(filePath).toFile();
    	if (!bundleFile.exists()) {
    		try {
				createBundleFile(bundleFile, fileManager);
			} catch (IOException e) {
				logger.error(e, filePath.toString());
			}
    	} // TODO - Use timestamps to determine if file is cached from previous session
        return bundleFile;
	}

	/**
	 * Extracts file from bundle as a stream and copies it to locaction controlled by file manager
	 * @param bundleFile
	 * @param fileManager
	 * @throws IOException
	 */
	private void createBundleFile(File bundleFile, FileManager fileManager) throws IOException {
    	URL url = new URL("platform:/plugin/" + AndworxBuildPlugin.PLUGIN_ID + "/" + filePath);
    	try (InputStream inputStream = url.openConnection().getInputStream()) {
    		bundleFile = fileManager.saveFile(filePath, inputStream);
    	}
	}
}
