/*
 * Copyright (C) 2012 The Android Open Source Project
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
package org.eclipse.andmore.android.multidex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Scanner;

import org.eclipse.andmore.android.AndroidPlugin;
import org.eclipse.andmore.android.common.log.AndmoreLogger;
import org.eclipse.andmore.android.common.log.UsageDataConstants;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.osgi.framework.Bundle;

import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;

/**
 * Manager to enable MultiDex for projects with too many classes. 
 * 
 * Add two properties to the project.properties:
 * multidex.enabled=true
 * multidex.main-dex-list=main-dex-list.txt
 * 
 * Also copies the main-dex-list.txt file
 */
public class MultiDexManager {

	private static final String MULTIDEX_MAIN_DEX_LIST_PATH = "/files/main-dex-list.txt";
	private static final String MULTIDEX_MAIN_DEX_LIST_FILENAME = "main-dex-list.txt";

	private static final String MULTIDEX_ENABLED_PROPERTY = "multidex.enabled";
	private static final String MULTIDEX_ENABLED_STATEMENT = MULTIDEX_ENABLED_PROPERTY + "=true";
	
	private static final String MULTIDEX_MAIN_DEX_LIST_PROPERTY = "multidex.main-dex-list";
	private static final String MULTIDEX_MAIN_DEX_LIST_STATEMENT = MULTIDEX_MAIN_DEX_LIST_PROPERTY + "=main-dex-list.txt";

	private static final String DEFAULT_PROPERTIES_FILENAME = "default.properties";
	private static final String PROJECT_PROPERTIES_FILENAME = "project.properties";

	private static final String NL = System.getProperty("line.separator");
	
	/**
	 * Prepares the project with the MultiDex settings
	 * 
	 * @param project
	 * @param monitor
	 * @return
	 */
	public static IStatus enableMultiDex(IProject project, IProgressMonitor monitor) {
		IStatus status = Status.OK_STATUS;
		
		File projectPropertiesFile = project.getFile(PROJECT_PROPERTIES_FILENAME).getLocation().toFile();
		File defaultPropertiesFile = project.getFile(DEFAULT_PROPERTIES_FILENAME).getLocation().toFile();
		if (projectPropertiesFile.canWrite()) {
			defaultPropertiesFile = projectPropertiesFile;
		}
		
		try {
			addMultiDexEnabledStatement(defaultPropertiesFile);
			
			File mainDexListFile = getMainDexListFile(project);
			if(mainDexListFile == null) {
				addMainDexListStatement(defaultPropertiesFile);
				if (!fileExists(mainDexListFile)) {
					copyMainDexListFile(project, monitor);
				}
			}
			try {
				project.refreshLocal(IResource.DEPTH_ONE, null);
			} catch (CoreException e) {
				// Do nothing, user just have to press F5
			}
		} catch (Exception e) {
			AndmoreLogger.error(MultiDexManager.class, "Error while setting main-class-list for use with multidex", e);
			status = new Status(IStatus.ERROR, AndroidPlugin.PLUGIN_ID, "Could not enable multidex", e);
		}

		AndmoreLogger.collectUsageData(UsageDataConstants.WHAT_OBFUSCATE, UsageDataConstants.KIND_OBFUSCATE,
				UsageDataConstants.DESCRIPTION_DEFAULT, AndroidPlugin.PLUGIN_ID, AndroidPlugin.getDefault().getBundle()
						.getVersion().toString());

		return status;
	}

	/**
	 * Removes MultiDex settings 
	 * 
	 * @param project
	 * @return
	 */
	public static IStatus disableMultiDex(IProject project) {
		IStatus status = Status.OK_STATUS;
		
		File projectPropertiesFile = project.getFile(PROJECT_PROPERTIES_FILENAME).getLocation().toFile();
		File defaultPropertiesFile = project.getFile(DEFAULT_PROPERTIES_FILENAME).getLocation().toFile();
		if (projectPropertiesFile.canWrite()) {
			defaultPropertiesFile = projectPropertiesFile;
		}

		try {
			removeMultiStatements(defaultPropertiesFile);
			
			try {
				project.refreshLocal(IResource.DEPTH_ONE, null);
			} catch (CoreException e) {
				// Do nothing, user just have to press F5
			}
		} catch (Exception e) {
			AndmoreLogger.error(MultiDexManager.class, "Error while removing MultiDex settings", e);
			status = new Status(IStatus.ERROR, AndroidPlugin.PLUGIN_ID, "Could not remove MultiDex settings", e);
		}

		AndmoreLogger.collectUsageData(UsageDataConstants.WHAT_OBFUSCATE, UsageDataConstants.KIND_DESOBFUSCATE,
				UsageDataConstants.DESCRIPTION_DEFAULT, AndroidPlugin.PLUGIN_ID, AndroidPlugin.getDefault().getBundle()
						.getVersion().toString());

		return status;
	}

	/*
	 * @param propertiesFile file to write
	 * 
	 * @param newContent content to write (replaces entire file)
	 * 
	 * @throws IOException
	 */
	private static void write(File propertiesFile, String newContent) throws IOException {
		Writer out = new OutputStreamWriter(new FileOutputStream(propertiesFile));
		try {
			out.write(newContent);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	/*
	 * @param ignoreMultiDexStatement true it does not return the lines with
	 * multidex.config and multidex.enabled
	 * 
	 * @return
	 * 
	 * @throws IOException
	 */
	private static String read(File propertiesFile, boolean ignoreMultiDexStatement) throws IOException {
		StringBuilder text = new StringBuilder();
		Scanner scanner = null;
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(propertiesFile);
			scanner = new Scanner(stream);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (!ignoreMultiDexStatement || (!line.contains(MULTIDEX_MAIN_DEX_LIST_PROPERTY) && !line.contains(MULTIDEX_ENABLED_PROPERTY))) {
					text.append(line + NL);
				}
			}
		} finally {
			try {
				if (scanner != null) {
					scanner.close();
				}
				if (stream != null) {
					stream.close();
				}
			} catch (IOException e) {
				AndmoreLogger.info("Could not close stream while reading project.properties file. " + e.getMessage());
			}
		}
		return text.toString();
	}

	private static boolean fileExists(File multiDexFile) {
		return (multiDexFile != null) && multiDexFile.exists();
	}

	/**
	 * Add the following line to file multidex.enabled=true if it does
	 * not exist yet
	 * 
	 * @param project
	 * @throws IOException
	 */
	private static void addMultiDexEnabledStatement(File propertiesFile) throws IOException {
		String currentContent = null;
		currentContent = read(propertiesFile, false);
		if (!currentContent.toString().contains(MULTIDEX_ENABLED_STATEMENT)) {
			String newContent = currentContent.endsWith(NL) ? currentContent + MULTIDEX_ENABLED_STATEMENT
					: currentContent + NL + MULTIDEX_ENABLED_STATEMENT;
			write(propertiesFile, newContent);
		}
	}
	
	/**
	 * Add the following line to file multidex.main-dex-list=main-dex-list.txt if it does
	 * not exist yet
	 * 
	 * @param project
	 * @throws IOException
	 */
	private static void addMainDexListStatement(File propertiesFile) throws IOException {
		String currentContent = null;
		currentContent = read(propertiesFile, false);
		if (!currentContent.toString().contains(MULTIDEX_MAIN_DEX_LIST_STATEMENT)) {
			String newContent = currentContent.endsWith(NL) ? currentContent + MULTIDEX_MAIN_DEX_LIST_STATEMENT
					: currentContent + NL + MULTIDEX_MAIN_DEX_LIST_STATEMENT;
			write(propertiesFile, newContent);
		}
	}

	/**
	 * Remove any of the following lines from project.properties (multidex.enabled=true, multidex.config=main-dex-list.txt)
	 * exists
	 * 
	 * @param project
	 * @throws IOException
	 */
	private static void removeMultiStatements(File propertiesFile) throws IOException {
		String contentWithoutMultiDexStatement = null;
		contentWithoutMultiDexStatement = read(propertiesFile, true);
		write(propertiesFile, contentWithoutMultiDexStatement);
	}

	private static void copyMainDexListFile(IProject project, IProgressMonitor monitor) throws IOException, CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor);

		URL mainDexListURL = getMultiDexKeepFileURL();
		if (mainDexListURL != null) {
			InputStream is = null;
			try {
				is = mainDexListURL.openStream();
				IFile destFile = project.getFile(MULTIDEX_MAIN_DEX_LIST_FILENAME);
				destFile.create(is, IResource.NONE, subMonitor);
			} finally {
				if (is != null) {
					is.close();
				}
			}
		}
	}

	/**
	 * Get the most suitable main-class-list file, either from SDK or our plug-in
	 * 
	 * @return the URL of the best main-class-list file found
	 */
	private static URL getMultiDexKeepFileURL() {
		Bundle bundle = AndroidPlugin.getDefault().getBundle();
		URL fileURL = bundle.getEntry(MULTIDEX_MAIN_DEX_LIST_PATH);

		return fileURL;
	}
	
	public static boolean isMultiDexEnabled(IProject project) {
		// get the project info
		ProjectProperties projectProperties = getProjectProperties(project);

        // this can happen if the project has no project.properties.
        if (projectProperties == null) {
            return false;
        }
        
        String multiDexEnabled = projectProperties.getProperty(MULTIDEX_ENABLED_PROPERTY);
        if(multiDexEnabled == null) {
        	return false;
        }
        
        return Boolean.parseBoolean(multiDexEnabled);
	}
	
	public static File getMainDexListFile(IProject project) {
		// get the project info
		ProjectProperties projectProperties = getProjectProperties(project);

        // this can happen if the project has no project.properties.
        if (projectProperties == null) {
            return null;
        }
        
        String mainDexListLocation = projectProperties.getProperty(MULTIDEX_MAIN_DEX_LIST_PROPERTY);
        if(mainDexListLocation == null) {
        	return null;
        }
        
        return project.getFile(mainDexListLocation).getLocation().toFile();
	}
	
	private static ProjectProperties getProjectProperties(IProject project) {
		String projectLocation = project.getLocation().toOSString();
		
		// legacy support: look for default.properties
        //ProjectProperties properties = ProjectProperties.load(projectLocation,
        //        PropertyType.LEGACY_DEFAULT);
        //if(properties == null) {
        //	properties = ProjectProperties.load(projectLocation, PropertyType.PROJECT);
        //}
        
        return ProjectProperties.load(projectLocation, PropertyType.PROJECT);
	}
}
