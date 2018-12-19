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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.eclipse.andworx.file.FileManager;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;

/**
 * Resources adapter for use of Sqlite database on Andworx
 */
public class SqliteEnvironment implements ResourceEnvironment {

	/** JPA persistence.xml located in existing plugin META-INF directory */
    public static final String DEFAULT_RESOURCE_LOCATION = "META-INF";

    public static interface ResourceOpener {
    	InputStream openResource(String resourceName) throws IOException;
    }

    private final String pluginId;
    /** Set locale for consistent operations involing text */
    private Locale locale = Locale.US;
    /** Path to be prepended to resource names */
    private final String resourceLocation;
    /** File system location for databases */
    private final File databaseDirectory;
    /** ClassLoader object for creating entity classes specified in persistence.xml */
    private final EntityClassLoader entityClassLoader;
    private final FileManager fileManager;

    /**
     * Construct SqliteEnvironment object
     * @param resourceLocation Path to be prepended to resource names
     * @param databaseDirectory File system location for databases
     * @param entityClassLoader ClassLoader object for creating entity classes specified in persistence.xml
     * @param fileManager File manager needed to store resource files from the bundle
     */
    public SqliteEnvironment(
    		String pluginId,
    		String resourceLocation, 
    		File databaseDirectory, 
    		EntityClassLoader entityClassLoader, 
    		FileManager fileManager)
    {
    	this.pluginId = pluginId;
        this.resourceLocation = resourceLocation;
        this.databaseDirectory = databaseDirectory;
    	this.entityClassLoader = entityClassLoader;
    	this.fileManager = fileManager;
    }
    
    /**
     * Provides read access to a resource stream such as a file.
     * @param resourceName Name of resource
     * @throws IOException
     */
    @Override
    public InputStream openResource(String resourceName) throws IOException 
    {   // Use resource path to fetch bundle file
		String resourcePath = resourceLocation + "/" + resourceName;
    	// The bundle file is extracted from the plugin jar and cached on the file system
	    BundleFileModule bundleFileModule = new BundleFileModule(pluginId, resourcePath);
    	File resourceFile = bundleFileModule.provideFile(fileManager);
    	if (resourceFile == null)
    		throw new IOException("Failed to open resource " + resourceName);
        InputStream instream = new FileInputStream(resourceFile);
        return instream;
    }

    /**
     * Get locale. 
     * Android lint complains if Locale is omitted where it can be specified as an optional parameter.
     */
    @Override
    public Locale getLocale() 
    {
        return locale;
    }

    /**
     * Returns database location when ConnectionType = "file"
     * @return File object for a directory location
     */
	@Override
	public File getDatabaseDirectory() {
		if ((databaseDirectory != null) && !databaseDirectory.exists())
			databaseDirectory.mkdirs();
		return databaseDirectory;
	}

    /**
     * Returns Class Loader for instantiating entity classes
     * @return EntityClassLoader object
     */
	@Override
	public EntityClassLoader getEntityClassLoader() {
		return entityClassLoader;
	}
}
