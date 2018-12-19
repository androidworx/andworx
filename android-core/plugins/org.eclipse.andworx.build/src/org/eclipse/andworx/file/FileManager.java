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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;

import com.android.builder.utils.FileCache;
import com.google.common.base.Charsets;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;

 /**
  * Manager of file cache and bundle files
  */
public class FileManager {
	/** Name of root directory in data area */
    public static String FILES_ROOT = "files";
	private static SdkLogger logger = SdkLogger.getLogger(FileManager.class.getName());
	
    /** Workspace location in which to locate the files */
    private final File dataArea;
	/** Utility to facilitate project builds */
	private final BuildHelper buildHelper;
	/** Plugin version to apply to file cache */
	private final String pluginVersion;
	
	/**
	 * Construct FileManager object
	 * @param pluginVersion Input parameter required when using {@link FileCache}
	 */
    public FileManager(File dataArea, String pluginVersion) {
    	assert(dataArea != null);
    	this.dataArea = new File(dataArea, FILES_ROOT);
		this.pluginVersion = pluginVersion;
		buildHelper = AndworxFactory.instance().getBuildHelper();
	}

    /**
     * Returns plugin version
     * @return version
     */
    public String getPluginVersion() {
		return pluginVersion;
	}

	/**
	 * Returns flag set true if given file is saved to data area
	 * @param filePath File path
	 * @return boolean
	 */
	public boolean containsFile(String filePath) {
    	File dataFile = new File(dataArea, filePath);
    	return dataFile.exists();
	}
	
    /**
     * Save given file to data area
     * @param toSave File
     */
    public void saveFile(File toSave) {
    	Path targetPath;
		targetPath = getTargetPath(toSave.getName());
		copy(toSave, targetPath);
    }

    /**
     * Save given file to data area
     * @param toSave File
     * @throws IOException 
     */
    public File saveFile(String filePath, InputStream inputStream) throws IOException {
    	Path targetPath;
		targetPath = getTargetPath(filePath);
		copy(inputStream, targetPath);
		return targetPath.toFile();
    }
 
    /**
     * Returns path to where file of given name is located in the data area 
     * @param filePath File path
     * @return Path object
     */
    public Path getTargetPath(String filePath) {
    	return dataArea.toPath().resolve(filePath);
    }

    /**
     * Prepares path for given file, cleaning it if not empty otherwise creating it if does not exist.
     * Then returns path.
     * @param filePath File path
     * @return Path object
     * @throws IOException
     */
	public Path prepareTargetPath(String filePath) throws IOException {
		Path path = getTargetPath(filePath);
		buildHelper.prepareDir(path.toFile());
		return path;
	}

	public boolean copyFile(String filePath, File destination) {
    	File dataFile = new File(dataArea, filePath);
    	if (dataFile.exists()) {
    		try {
				buildHelper.prepareDir(destination);
				return copy(dataFile, destination.toPath());
			} catch (IOException e) {
				logger.error(e, dataFile.getAbsolutePath());
			}
    	}
    	return false;
    }

	/**
	 * Returns content of text file in data area
	 * @param filePath File path
	 * @return file content or empty string if an error occurs
	 */
	public String readAsCharSource(String filePath) {
    	File dataFile = new File(dataArea, filePath);
    	if (dataFile.exists()) {
    		CharSource source = com.google.common.io.Files.asCharSource(dataFile, Charsets.UTF_8);
    		try {
				return source.read();
			} catch (IOException e) {
				logger.error(e, dataFile.getAbsolutePath());
			}
    	}
    	// Failure results in empty string.
		return "";
	}

	/**
	 * Write text to file in data area
	 * @param filePath File path
	 * @param charSequence Text to write
	 * @return flag set true if write succeeded
	 */
	public boolean writeAsCharSink(String filePath, CharSequence charSequence) {
    	File dataFile = new File(dataArea, filePath);
    	if (dataFile.exists()) 
             dataFile.delete();
    	CharSink sink = com.google.common.io.Files.asCharSink(dataFile, Charsets.UTF_8);
		try {
			sink.write(charSequence);
			return true;
		} catch (IOException e) {
			logger.error(e, dataFile.getAbsolutePath());
		}
		return false;
	}

	/**
	 * Copy file to given destination
	 * @param source Input file
	 * @param destination Output file
	 * @return flag set true if copy suceeded
	 */
	private boolean copy(File source, Path destination) {
		try (InputStream inputStream = new FileInputStream(source)) {
			copy(inputStream, destination);
			return true;
		} catch (IOException e) {
			logger.error(e, source.getAbsolutePath());
		}
		return false;
	}

	/**
	 * Copy file stream to given destination
	 * @param inputStream Input stream
	 * @param destination Output file
	 * @throws IOException
	 */
	private void copy(InputStream inputStream, Path destination) throws IOException {
		buildHelper.prepareDir(destination.toFile());
		Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
	}


}
