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

import javax.inject.Singleton;

import au.com.cybersearch2.classyjpa.entity.JavaPersistenceContext;
import au.com.cybersearch2.classyjpa.entity.PersistenceContainer;
import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.entity.TaskBase;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classytask.Executable;
import au.com.cybersearch2.classytask.JavaThreadMessenger;
import au.com.cybersearch2.classytask.TaskManager;
import au.com.cybersearch2.classytask.TaskMessenger;
import au.com.cybersearch2.classytask.TaskRunner;
import au.com.cybersearch2.classytask.ThreadHelper;
import dagger.Module;
import dagger.Provides;

/**
 * Sets up context for JPA operations by entity manager
 * Local copy of #au.com.cybersearch2.classyjpa.entity.PersistenceWorkModule 
 */
@Module
public class PersistenceWorkModule {
	/** Persistence unit name */
    private String puName;
    /** Flag set true if work is performed on a forked thread */
    private boolean async;
    /** Flag set true if user controls transaction scope */
    private boolean isUserTransactions;
    /** Task to be performed requiring an EntityManager object */
    private PersistenceWork persistenceWork;
 
    /**
     * Construct PersistenceWorkModule object
     * @param puName Persistence unit name
     * @param async Flag set true if work is performed on a forked thread
     * @param persistenceWork Task to be performed requiring an EntityManager object
     */
    public PersistenceWorkModule(String puName, boolean async, PersistenceWork persistenceWork)
    {
        this.puName = puName;
        this.async = async;
        this.persistenceWork = persistenceWork;
    }
 
    /**
     * Returns object which executes persistence task passed in constructor
     * @param persistenceContainer Provides persistence context in which the task will run
     * @param threadHelper Thread helper does nothing on Eclipse
     * @param taskManager Manages the background task thread pool
     * @param taskMessenger Signals task outcome
     * @return Executable object
     */
    @Provides 
    Executable provideExecutable(
            PersistenceContainer persistenceContainer,
            ThreadHelper threadHelper, 
            TaskManager taskManager, 
            TaskMessenger taskMessenger)
    {
        persistenceContainer.setUserTransactionMode(isUserTransactions);
        // Bind the task to the persistence context
        JavaPersistenceContext jpaContext = persistenceContainer.getPersistenceTask(persistenceWork);
        if (!async)
            return jpaContext.executeInProcess();
        else
        {   // Assemble task components
            TaskBase  task = new TaskBase(jpaContext, threadHelper);
            TaskRunner taskRunner = new TaskRunner(taskManager, taskMessenger);
            // Execute task in forked thread
            taskRunner.execute(task);
            return task.getExecutable();
        }
        
    }
    
    @Provides @Singleton 
    PersistenceContainer providePersistenceContainer(PersistenceContext persistenceContext)
    {   // Returns PersistenceContainer object providing persistence context in which the task will run
        return new PersistenceContainer(persistenceContext, puName, async);
    }
  
    @Provides 
    TaskMessenger provideTaskMessenger()
    {   // Returns object to signal task outcome
        return new JavaThreadMessenger();
    }
    
    /**
     * Set user transaction mode. The transaction is accessed by calling EntityManager getTransaction() method.
     * @param isUserTransactions boolean
     */
    public void setUserTransactions(boolean isUserTransactions)
    {
        this.isUserTransactions = isUserTransactions;
    }

    public PersistenceWork getPersistenceWork()
    {
        return persistenceWork;
    }
}
