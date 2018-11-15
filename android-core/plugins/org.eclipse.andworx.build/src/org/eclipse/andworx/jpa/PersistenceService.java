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
package org.eclipse.andworx.jpa;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.datatools.connectivity.drivers.DriverManager;

import au.com.cybersearch2.classyjpa.EntityManagerLite;
import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classytask.WorkStatus;

/**
 * Takes persistence tasks and places them on a queue so they can be executed sequentially.
 * Call service start() method to commence service
 * Wait on this object to block the caller thread until the queue is cleared. 
 * Call service stop() method to clean up resources
 */
public class PersistenceService {

    /** PersistenceUnitAdmin Unit name to look up configuration details in persistence.xml */
    static public final String PU_NAME = "org.eclipse.andworx.build";
	/** Name of dynamically configured driver configured for archive "sqlite-jdbc-3.8.5-pre1.jar" */
	public static final String JDBC_DRIVER_INSTANCE_NAME = "h2_driver";
	/** Name of template to which "sqlite_jdbc_driver" is assigned. Note driver version indicates compatibility, not what is actully used */
	private static final String TEMPLATE_ID = "org.eclipse.datatools.connectivity.db.generic.genericDriverTemplate";
	public static final String SERVICE_NAME = "Persistence Service";
	//private static final String SQL_DRIVER_RESOURCE = "libs/sqlite-jdbc-3.8.5-pre1.jar"; 
    public static int MAX_QUEUE_LENGTH = 16;
    public static String[][] CONNECTION_PROPERTIES = new String[][] {
    	{ "USER", System.getProperty("user.name", "sa") },
    	{ "DB_CLOSE_ON_EXIT", "FALSE" },
    	{ "AUTO_SERVER", "TRUE" }
    };

    /** Task which configures the persistence context */
	public interface CallableTask {
		String getName();
		void call(PersistenceContext persistenceContext) throws Exception;
	}
	
    private static SdkLogger logger = SdkLogger.getLogger(PersistenceService.class.getName());

    /** Blocking task queue */
    private BlockingQueue<PersistenceWork> taskQueue;
    /** Service thread to consume persistence tasks placed on queue */
    private Thread consumeThread;
    /** Persistence context */
    private final PersistenceContext persistenceContext;

    /**
     * Construct PersistenceService object
     */
	public PersistenceService(PersistenceContext persistenceContext) {
		this.persistenceContext = persistenceContext;
    	// Queue capacity allows for a generous maximum number of tasks to be queued
    	// Fair access for waiting threads is turned on but should not be needed
    	taskQueue = new ArrayBlockingQueue<PersistenceWork>(MAX_QUEUE_LENGTH, true);
	}

    /**
     * Inserts the specified persistence task into this queue if it is possible to do
     * so immediately without violating capacity restrictions. Otherwise poll until
     * the insert succeeds during which time the calling thread is blocked. 
     *
     * @param task Persistence task 
     */
	public void offerTask(PersistenceWork task) throws InterruptedException {
		while (!taskQueue.offer(task))
			Thread.sleep(500);
	}
	
	/**
	 * Offer a Callable Task requiring the persistence context to be exectuted by this service.
	 * Use this method to set named queries in the persistence context
	 * @param callable
	 * @throws InterruptedException 
	 */
	public void offer(CallableTask callable) throws InterruptedException {
        offerTask(new PersistenceWork(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	try {
					callable.call(persistenceContext);
				} catch (Exception e) {
	            	entityManager.getTransaction().setRollbackOnly();
	            	logger.error(e, callable.getName() + "failed");
				}
            }
            
            @Override
            public void onPostExecute(boolean success)
            {
                if (!success) {
                    throw new AndworxException(callable.getName() + "failed");
                }
            }

            @Override
            public void onRollback(Throwable rollbackException)
            {
                throw new AndworxException(callable.getName() + "failed", rollbackException);
            }
        });
	}
	
	/**
	 * Start service. NOTE: the persistence context must be initialized prior to calling this method.
	 * @param persistenceRunner Object which executes persistence work passed in constructor
	 */
	public void start(PersistenceRunner persistenceRunner) {
		// Only action once
		if ((consumeThread != null) && consumeThread.isAlive())
			return;
		final PersistenceService self = this;
		// Create consumer thread
        Runnable comsumeTask = new Runnable()
        {
            @Override
            public void run() 
            {
				// Register Sqlite JDBC driver with Eclipse Database Tools disabled for h2
				//registerDriver();
				logger.info(SERVICE_NAME + " initalized successfully");
                while (true)
                {
                    try 
                    {
                    	PersistenceWork task = taskQueue.take();
                   	    if (task != null) { // Paranoid null check
                   	    	WorkStatus workStatus = persistenceRunner.run(task).waitForTask();
                   	    	if (workStatus != WorkStatus.FINISHED) {
                   	    		logger.warning("Persistence task terminated prematurely");
                   	    	}
                    	} else break; // This is not expected to happen
                    	if (taskQueue.isEmpty())
                    		// Notify when queue becomes empty
	                        synchronized(self) {
	                        	self.notifyAll();
	                        }
                    } 
                    catch (InterruptedException e) 
                    {
                		// Notify when interrupt stops service
                        synchronized(self) {
                        	self.notifyAll();
                        }
                        break;
                    }
                }
                while (!taskQueue.isEmpty()) {
        			taskQueue.remove();
        		}
            }};
        consumeThread = new Thread(comsumeTask, SERVICE_NAME);
        consumeThread.start();
	}
	
	/**
	 * Stop service
	 */
	public synchronized void stop() {
		if (consumeThread != null) {
			consumeThread.interrupt();
		    consumeThread = null;
			logger.info(SERVICE_NAME + " stopped");
		}
	}
	
	/**
	 * Register driver for Eclipse Data Tools Development
	 */
	/*
	private void registerDriver() {
		try {
			assertDriverExistsAtModel();
		} catch (IOException | URISyntaxException e) {
        	logger.error(e, "Error registering JDBC driver " + JDBC_DRIVER_INSTANCE_NAME);
		}
	}
	*/

	/**
	 * Checks if a compatible JDBC driver is registered. If not, registers one.
	 * @throws URISyntaxException 
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	/*
	private static void assertDriverExistsAtModel() throws IOException, URISyntaxException {
		DriverManager driverMan = DriverManager.getInstance();
		String allDrivers = driverMan.getFullJarList();
		File file = AndworxFactory.instance().getBundleFile(SQL_DRIVER_RESOURCE);
    	if (file == null)
    		throw new IOException("Failed to open resource " + SQL_DRIVER_RESOURCE);
		String driverPath = file.getAbsolutePath();
		if ((allDrivers == null) || (!allDrivers.contains(driverPath))) {
			String templateId = TEMPLATE_ID;
			driverMan.createNewDriverInstance(templateId, JDBC_DRIVER_INSTANCE_NAME, driverPath);
			logger.info("Created a JDBC driver instance at Data Tools."); 
		}
	}
	*/
}
