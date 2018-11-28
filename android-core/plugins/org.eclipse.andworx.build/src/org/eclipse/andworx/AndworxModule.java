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
import java.util.Properties;

import javax.inject.Singleton;

import org.eclipse.andworx.build.AndworxIssueReport;
import org.eclipse.andworx.config.SecurityController;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.andworx.maven.MavenServicesProvider;
import org.eclipse.andworx.process.java.JavaQueuedProcessor;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.TransformAgent;
import org.eclipse.m2e.core.MavenPlugin;

import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.utils.ILogger;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classydb.ConnectionSourceFactory;
import au.com.cybersearch2.classydb.DatabaseSupport;
//import au.com.cybersearch2.classydb.SQLiteDatabaseSupport;
import au.com.cybersearch2.classydb.H2DatabaseSupport;
import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;
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
public class AndworxModule {
    private final File databaseDirectory;
    private final EntityClassLoader entityClassLoader;
    private final File dataArea;
    private final FileCache userFileCache;
    private final ILogger logger;
    
    /** SQLite database adapter */
   // private SQLiteDatabaseSupport sqliteDatabaseSupport;
    private H2DatabaseSupport h2DatabaseSupport;

    /**
     * Construct AndworxModule object
     * @param resourceEnvironment Resources adapter
     */
    public AndworxModule(File databaseDirectory, EntityClassLoader entityClassLoader, File dataArea, FileCache userFileCache,  ILogger logger)
    {
        this.databaseDirectory = databaseDirectory;
        this.entityClassLoader = entityClassLoader;
        this.dataArea = dataArea;
        this.userFileCache = userFileCache;
        this.logger = logger;
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
    DatabaseSupport provideDatabaseSupport(ResourceEnvironment resourceEnvironment)
    {   // Returns Sqlite database adapter configured for file system persistence 
        //sqliteDatabaseSupport = new SQLiteDatabaseSupport(resourceEnvironment.getDatabaseDirectory());
        //return sqliteDatabaseSupport;    
        h2DatabaseSupport = new H2DatabaseSupport(resourceEnvironment.getDatabaseDirectory()); 
        return h2DatabaseSupport;     
    }
    
    @Provides @Singleton 
    PersistenceFactory providePersistenceFactory(DatabaseSupport databaseSupport, ResourceEnvironment resourceEnvironment)
    {   // Returns PersistenceFactory object which creates the configured perisistence unit(s)
    	PersistenceFactory persistenceFactory = new PersistenceFactory(databaseSupport, resourceEnvironment);
    	// Add user property and custom settings to be appended to connection URL
    	Properties props = persistenceFactory.getPersistenceUnit(PersistenceService.PU_NAME)
    	                  .getPersistenceAdmin()
    	                  .getProperties();
    	for (String[] item: PersistenceService.CONNECTION_PROPERTIES)
    		props.put(item[0], item[1]);
        return persistenceFactory;
    }

    @Provides @Singleton 
    ConnectionSourceFactory provideConnectionSourceFactory()
    {   // Returns ConnectionSourceFactory for Sqlite
        //return sqliteDatabaseSupport;
        return h2DatabaseSupport;     
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
    
    @Provides @Singleton 
    AndroidConfiguration provideAndroidConfiguration(PersistenceService persistenceService) 
    {
    	return new AndroidConfiguration(persistenceService);
    }

    @Provides @Singleton
    SecurityController provideSecurityController() {
    	return new SecurityController();
    }
    
    @Provides @Singleton 
    MavenServices provideMavenServices() {
    	return new MavenServicesProvider(MavenPlugin.getMaven());
    }
    
    @Provides @Singleton 
    FileManager provideFileManager() {
    	return new FileManager(dataArea, userFileCache, AndworxConstants.ANDWORX_BUILD_VERSION);
    }
    
    @Provides @Singleton
    BuildElementFactory provideBuildElementFactory() {
    	return new BuildElementFactory();
    }
    
    @Provides @Singleton
    BuildHelper provideBuildHelper(BuildElementFactory buildElementFactory) {
    	return new BuildHelper(buildElementFactory);
    }
    
    @Provides @Singleton
    JavaQueuedProcessor provideJavaQueuedProcessor() {
     	return new JavaQueuedProcessor(0 /*processesNumber*/);
    }
    
    @Provides @Singleton
    TaskFactory provideTaskFactory() {
    	return new TaskFactory();
    }
    
    @Provides @Singleton
    ProcessExecutor provideProcessExecutor() {
    	return new DefaultProcessExecutor(logger);
    }
    
    @Provides @Singleton
    JavaProcessExecutor provideJavaProcessExecutor() {
    	// Required AndroidBuilder parameter
    	return new JavaProcessExecutor(){
			@Override
			public ProcessResult execute(JavaProcessInfo javaProcessInfo,
					ProcessOutputHandler processOutputHandler) {
				throw new UnsupportedOperationException();
			}};
    }
    
    @Provides @Singleton
    AndworxIssueReport provideAndworxIssueReport() {
    	return new AndworxIssueReport(logger);
    }
    
    @Provides @Singleton
    TransformAgent provideTransformAgent() {
    	return new TransformAgent();
    }
    
    @Provides @Singleton
    WaitableExecutor provideWaitableExecutor() {
		return WaitableExecutor.useGlobalSharedThreadPool();
    }
    
    
    @Provides @Singleton
    ProjectRegistry provideProjectRegistry() {
    	return new ProjectRegistry();
    }
}
