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
package org.eclipse.andworx.topology;

import java.util.Properties;

import javax.inject.Singleton;

import org.eclipse.andworx.AndworxThreadHelper;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.modules.WorkspaceConfiguration;
import org.eclipse.andworx.modules.WorkspaceModeller;
import org.eclipse.andworx.tree.TreeFactory;
import org.eclipse.e4.core.services.events.IEventBroker;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classydb.ConnectionSourceFactory;
import au.com.cybersearch2.classydb.DatabaseSupport;
import au.com.cybersearch2.classydb.H2DatabaseSupport;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classyjpa.persist.PersistenceFactory;
import au.com.cybersearch2.classytask.TaskManager;
import au.com.cybersearch2.classytask.ThreadHelper;
import dagger.Module;
import dagger.Provides;

/**
 * Dagger dependency injection main module.
 */
@Module
public class ModelModule {
    private final ResourceEnvironment resourceEnvironment;
    private final IEventBroker eventBroker;
    private ModelNodeBeanFactory modelNodeBeanFactory;
    
    /** SQLite database adapter */
   // private SQLiteDatabaseSupport sqliteDatabaseSupport;
    private H2DatabaseSupport h2DatabaseSupport;

    /**
     * Construct AndworxModule object
     * @param resourceEnvironment Resources adapter
     */
    public ModelModule(
    		ResourceEnvironment resourceEnvironment, 
     		IEventBroker eventBroker)
    {
        this.resourceEnvironment = resourceEnvironment;
        this.eventBroker = eventBroker;
     }

    @Provides  @Singleton
    ResourceEnvironment provideResourceEnvironment() {
    	return resourceEnvironment;
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
    DatabaseSupport provideDatabaseSupport(ResourceEnvironment resourceEnvironment)
    {   // Returns H2 database adapter configured for file system persistence 
        h2DatabaseSupport = new H2DatabaseSupport(resourceEnvironment.getDatabaseDirectory()); 
        return h2DatabaseSupport;     
    }
    
    @Provides @Singleton 
    PersistenceFactory providePersistenceFactory(DatabaseSupport databaseSupport, ResourceEnvironment resourceEnvironment)
    {   // Returns PersistenceFactory object which creates the configured perisistence unit(s)
    	PersistenceFactory persistenceFactory = new PersistenceFactory(databaseSupport, resourceEnvironment);
    	Properties props = persistenceFactory.getPersistenceUnit(ModelConstants.PU_NAME)
                .getPersistenceAdmin()
                .getProperties();
    	if (resourceEnvironment.getDatabaseDirectory() != null) {
    		// Only applicable to database located on hard drive:
	    	// Add user property and custom settings to be appended to connection URL
	    	for (String[] item: PersistenceService.CONNECTION_PROPERTIES)
	    		props.put(item[0], item[1]);
    	} else {
	    	for (String[] item: PersistenceService.TEST_CONNECTION_PROPERTIES)
	    		props.put(item[0], item[1]);
    	}
        return persistenceFactory;
    }

    @Provides @Singleton 
    ConnectionSourceFactory provideConnectionSourceFactory()
    {   // Returns ConnectionSourceFactory for Sqlite
        return h2DatabaseSupport;     
    }

    @Provides @Singleton 
    PersistenceContext providePersistenceContext(PersistenceFactory persistenceFactory, ConnectionSourceFactory connectionSourceFactory)
    {   // Returns PersistenceContext object which provides a persistence API
        return new PersistenceContext(persistenceFactory, connectionSourceFactory);
    }
 
    @Provides @Singleton 
    PersistenceService providePersistenceService(PersistenceContext persistenceContext) {
    	PersistenceService persistenceService =  new PersistenceService(persistenceContext, "Andworx Model " + PersistenceService.SERVICE_NAME);
    	// TreeFactory must set persistence service initial task before the service is started.
    	modelNodeBeanFactory = new TreeFactory(persistenceService, persistenceContext);
    	return persistenceService;
    }

    @Provides @Singleton 
    ModelNodeBeanFactory provideModelNodeBeanFactory(PersistenceContext persistenceContext, PersistenceService persistenceService) {
    	return modelNodeBeanFactory; 
    }

    @Provides @Singleton 
    WorkspaceConfiguration provideWorkspaceConfiguration(PersistenceService persistenceService) {
    	return new WorkspaceConfiguration(persistenceService);
    }


    @Provides @Singleton 
    WorkspaceModeller provideWorkspaceModeller(WorkspaceConfiguration workspaceConfiguration) {
    	return new WorkspaceModeller(workspaceConfiguration);
    }
}
