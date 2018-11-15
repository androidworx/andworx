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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;

import com.android.builder.utils.FileCache;
import com.android.utils.PathUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
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
    private File dataArea;
    /** A cache for already-created files/directories */
	private FileCache userFileCache;
	/** Plugin version to apply to file cache */
	private String pluginVersion;
	/** Maps cached file to file name */
	private Map<String, CachedFile> cachedFileMap;
	/** Temporary path for data area backup. Used as fallback if data area not set */
	private Path tempPath;
	// Utility to facilitate project builds
	private BuildHelper buildHelper;
	
	/**
	 * Construct FileManager object
	 * @param pluginVersion Input parameter required when using {@link FileCache}
	 */
    public FileManager(File dataArea, FileCache userFileCache, String pluginVersion) {
    	this.dataArea = dataArea;
    	this.userFileCache = userFileCache;
		this.pluginVersion = pluginVersion;
		cachedFileMap = new HashMap<>();
		buildHelper = AndworxFactory.instance().getBuildHelper();
	}

    /**
     * Set Android SDK builder utils cache for already-created files/directories
     * @param userFileCache FileCache object
     */
    public void setFileCache(FileCache userFileCache) {
    	this.userFileCache = userFileCache;
    }

    /**
     * Returns plugin version
     * @return version
     */
    public String getPluginVersion() {
		return pluginVersion;
	}

    /**
     * Set data area where files are to saved
     * @param dataArea File object
     */
	public void setDataArea(File dataArea) {
		this.dataArea = new File(dataArea, FILES_ROOT);
    	if (tempPath != null) { // Copy temporary files to data area
    		File[] files = tempPath.toFile().listFiles();
    		if (files.length > 0) {
    			for (File file: files)
    				saveFile(file);
    		}
    	}
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
    public boolean saveFile(File toSave) {
    	Path targetPath;
    	if (dataArea != null) {
    		targetPath = getTargetPath(toSave.getName());
    		copy(toSave, targetPath);
    		return true;
    	}
    	if (tempPath == null) {
        	try {
    			tempPath = PathUtils.createTmpDirToRemoveOnShutdown(FileManager.class.getName());
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			logger.error(e,"Error creating temporary path");
    		}
    	}
    	if (tempPath != null) {
    		copy(toSave, tempPath.resolve(toSave.getName()));
    		return true;
    	}
    	return false;
    }

    /**
     * Save given file to data area
     * @param toSave File
     * @throws IOException 
     */
    public File saveFile(String filePath, InputStream inputStream) throws IOException {
    	Path targetPath;
    	if (dataArea != null) {
    		targetPath = getTargetPath(filePath);
    		copy(inputStream, targetPath);
    		return targetPath.toFile();
    	}
    	if (tempPath != null) {
    		targetPath = tempPath.resolve(filePath);
    		copy(inputStream, targetPath);
    		return targetPath.toFile();
    	}
    	throw new IOException("Cannot extract bundle file: " + filePath);
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

	/**
	 * Copy data area file to given destination 
	 * @param filePath Input file path
	 * @param destination Output directory
	 * @return flag set true if copy succeeded
	 */
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
