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

import javax.inject.Singleton;

import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.jpa.PersistenceService;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classydb.ConnectionSourceFactory;
import au.com.cybersearch2.classydb.DatabaseSupport;
import au.com.cybersearch2.classydb.SQLiteDatabaseSupport;
import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classyjpa.persist.PersistenceFactory;
import au.com.cybersearch2.classytask.TaskManager;
import au.com.cybersearch2.classytask.ThreadHelper;
import dagger.Module;
import dagger.Provides;

/**
 * Dagger persistence service moudle
 */
@Module
public class PersistenceServiceModule {
    private final File databaseDirectory;
    private final EntityClassLoader entityClassLoader;
   /** SQLite database adapter */
    private SQLiteDatabaseSupport sqliteDatabaseSupport;

    public PersistenceServiceModule(File databaseDirectory, EntityClassLoader entityClassLoader) {
        this.databaseDirectory = databaseDirectory;
        this.entityClassLoader = entityClassLoader;
    }

    @Provides  @Singleton
    ResourceEnvironment provideResourceEnvironment(FileManager fileManager) {
	    return	new SqliteEnvironment(
	    				"META-INF", 
	    				databaseDirectory, 
	    				entityClassLoader,
	    				fileManager);
    }
    
    @Provides @Singleton 
    ThreadHelper provideThreadHelper()
    {   // Returns "do nothing" helper for Eclipse
        return new AndworxThreadHelper();
    }
   
    @Provides @Singleton 
    TaskManager provideTaskManager()
    {   // Returns TaskManager object to manage the background task thread pool
        return new TaskManager();
    }

    @Provides @Singleton 
    DatabaseSupport provideDatabaseSupportResourceEnvironment(ResourceEnvironment resourceEnvironment)
    {   // Returns Sqlite database adapter configured for file system persistence 
        sqliteDatabaseSupport = new SQLiteDatabaseSupport(resourceEnvironment.getDatabaseDirectory());
        return sqliteDatabaseSupport;    
    }
    
    @Provides @Singleton 
    PersistenceFactory providePersistenceFactory(DatabaseSupport databaseSupport, ResourceEnvironment resourceEnvironment)
    {   // Returns PersistenceFactory object which creates the configured perisistence unit(s)
        return new PersistenceFactory(databaseSupport, resourceEnvironment);
    }

    @Provides @Singleton 
    ConnectionSourceFactory provideConnectionSourceFactory()
    {   // Returns ConnectionSourceFactory for Sqlite
        return sqliteDatabaseSupport;
    }

    @Provides @Singleton 
    PersistenceContext providePersistenceContext(PersistenceFactory persistenceFactory, ConnectionSourceFactory connectionSourceFactory)
    {   // Returns PersistenceContext object which provides a persistence API
        return new PersistenceContext(persistenceFactory, connectionSourceFactory);
    }
 
    @Provides @Singleton 
    PersistenceService providePersistenceService(PersistenceContext persistenceContext) {
    	return new PersistenceService(persistenceContext);
    }
    
}
