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
package org.eclipse.andworx.build;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.log4j.PropertyConfigurator;
import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.config.ProguardFile;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.CachedFile;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.file.FileResource;
import org.eclipse.andworx.helper.AndworxPrintStream;
import org.eclipse.andworx.helper.EclipseUiHelper;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.log.JavaLogger;
import org.eclipse.andworx.log.Log;
import org.eclipse.andworx.sdk.AndroidSdkHelper;
import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.andworx.sdk.AndroidSdkValidator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.builder.utils.FileCache;
import com.android.tools.r8.com.google.common.base.Throwables;
import com.android.utils.PathUtils;
import com.google.common.io.Files;
import com.j256.ormlite.logger.LoggerFactory;
import com.j256.ormlite.logger.LoggerFactory.LogType;

import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;

public class AndworxBuildPlugin extends AbstractUIPlugin {

	public static String PLUGIN_ID = "org.eclipse.andworx.build";
	private static final int START_JOBS_COUNT = 2;
	
    /** AndworxBuildPlugin instance retrieval blocks until plugin initialization is completed */
    private static class AndworxBuildPluginHolder {
        private AndworxBuildPlugin staticPlugin;
    	private volatile AndworxBuildPlugin dynamicPlugin;

    	public void setPlugin(AndworxBuildPlugin staticPlugin) {
			synchronized(this) {
				this.staticPlugin = staticPlugin;
				if (staticPlugin == null)
					dynamicPlugin = null;
			}
    	}
    	
    	public AndworxBuildPlugin getPlugin() {
    		while (dynamicPlugin == null) {
    			synchronized(this) {
    	    		if ((dynamicPlugin == null) && (staticPlugin != null)) {
    	    			dynamicPlugin = staticPlugin;
                        return dynamicPlugin;
    	    		}
    			}
    			try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
    		}
    		// Will return null if thread is interrupted before pluin start() is called
    		return dynamicPlugin;
    	}
    }
 
    private static final AndworxBuildPluginHolder pluginHolder = new AndworxBuildPluginHolder();
    private static Log logger = JavaLogger.getLogger(AndworxBuildPlugin.class.getName());

    /** SDK validator */
    private AndroidSdkValidator androidSdkValidator;
    private AndroidSdkHelper androidSdkHelper;
    /** Flag set true if ANDWORX_STARTED has been broadcast */
    private volatile boolean isStarted;
    /** A cache for already-created files/directories */  
	private FileCache userFileCache;
	/** Simple file logging <tt>Handler</tt> */
    private FileHandler fileTxt;
    /** Logging formatter to print a brief summary of the {@code LogRecord} in a human readable format */
    private SimpleFormatter formatterTxt;
    

    /** The global build console */
    private MessageConsole buildConsole;
    /** Stream to write in the build console */
    private MessageConsoleStream buildConsoleStream;

    /** Stream to write error messages to the build console */
    private MessageConsoleStream buildConsoleErrorStream;
    /** Color used in the error console */
    private Color colorRed;
    /** Application component object factory */
    private AndworxFactory objectFactory;
    /** Startup jobs counter */
    private AtomicInteger jobsCounter;
    private Object jobsMonitor;
    /** Event broker service */
    private IEventBroker eventBroker;
    /** Workspace location in which to locate the Database */
    private File dataArea;
    /** Preference store for persisting SDK location, referenced by function call to avoid potential I/O activity in the constructor. */
    Supplier<ScopedPreferenceStore> preferenceStoreSupplier = new Supplier<ScopedPreferenceStore> () {

		@Override
		public ScopedPreferenceStore get() {
			// Note: Base class caches value, so no need to cache it here.
			return (ScopedPreferenceStore) getPreferenceStore();
		}};

    /**
     * Construct AndworxBuildPlugin object
     */
	public AndworxBuildPlugin() {
		jobsCounter = new AtomicInteger();
		jobsMonitor = new Object();
		androidSdkHelper = new AndroidSdkHelper(preferenceStoreSupplier);
		androidSdkValidator = new AndroidSdkValidator(androidSdkHelper);
	}

	/**
	 * Returns flag set true if ANDWORX_STARTED has been broadcast
	 * @return boolean
	 */
	public boolean isStarted() {
		return isStarted;
	}
	
	/**
	 * Schedule job to run after startup has completed
	 * @param job Job to run
	 */
	public void scheduleJob(Job job) {
        IEclipseContext serviceContext = E4Workbench.getServiceContext(); 
        IEventBroker scheduleJobEeventBroker = (IEventBroker) serviceContext.get(IEventBroker.class.getName());
		synchronized (jobsMonitor) {
			if (jobsCounter.get() < START_JOBS_COUNT) {
		    	EventHandler eventHandler = new EventHandler() {
					@Override
					public void handleEvent(Event event) {
						job.schedule();
						scheduleJobEeventBroker.unsubscribe(this);
					}};
					scheduleJobEeventBroker.subscribe(AndworxEvents.ANDWORX_STARTED, eventHandler);
			} else
				job.schedule();
		}
	}
	
    /**
     * Returns the current display
     * @return the display
     */
    @NonNull
    public Display getDisplay() {
        synchronized (this) {
            IWorkbench bench = getWorkbench();
            if (bench != null) {
                Display display = bench.getDisplay();
                if (display != null) {
                    return display;
                }
            }
        }

        Display display = Display.getCurrent();
        if (display != null) {
            return display;
        }

        return Display.getDefault();
    }

    /**
     * Log error in workspace log file and on the Build console
     * @param exception
     * @param tag
     * @param format
     * @param args
     */
    public void logAndPrintError(Throwable exception, String tag,
            String format, Object ... args) {
        String message = String.format(format, args);
        Status status = new Status(IStatus.ERROR, PLUGIN_ID, message, exception);
        getLog().log(status);
        if (exception != null)
        	message += "\n" + Throwables.getStackTraceAsString(Throwables.getRootCause(exception));
        doPrintError(tag, message);
        showBuildConsole();
     }

    /**
     * start()
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        IEclipseContext serviceContext = E4Workbench.getServiceContext(); 
    	eventBroker = (IEventBroker) serviceContext.get(IEventBroker.class.getName());
        // set the default android console.
        buildConsole = new MessageConsole("Build", null);
        ConsolePlugin.getDefault().getConsoleManager().addConsoles(
                new IConsole[] { buildConsole });

        // get the stream to write in the android console.
        buildConsoleStream = buildConsole.newMessageStream();
        buildConsoleErrorStream = buildConsole.newMessageStream();
		IPath pluginDataPath = null;
		try {
			pluginDataPath = getStateLocation();
		} catch (IllegalStateException e) {
			// the system is running with no data area (-data @none),
			// or a data area has not been set yet
			throw new AndworxException("Plugin data area not configured", e);
		}
		dataArea = pluginDataPath.makeAbsolute().toFile();
		File cacheDirectory = null;
		File logDirectory = null;
		cacheDirectory = new File(pluginDataPath.makeAbsolute().toFile(), "cache");
		if (!cacheDirectory.exists() && !cacheDirectory.mkdirs())
			cacheDirectory = null;
		if (cacheDirectory == null)
			try {
				cacheDirectory = PathUtils.createTmpDirToRemoveOnShutdown(PLUGIN_ID + "." + "cache").toFile();
			} catch (IOException e) {
		    }
		if (cacheDirectory == null) {
			throw new AndworxException("File cache creation error");
		}
		logDirectory = new File(pluginDataPath.makeAbsolute().toFile(), "log");
		if (!logDirectory.exists() && !logDirectory.mkdirs()) {
			logDirectory = new File(System.getProperty("user.dir"), "andworx-logs");
			if (!logDirectory.exists() && !logDirectory.mkdirs())
				logDirectory = Files.createTempDir();
		}
    	// Set ORMLite system property to select local Logger
        System.setProperty(LoggerFactory.LOG_TYPE_SYSTEM_PROPERTY, LogType.LOG4J.name());
	    // Set system property to locate ORMLite file appender location
		System.setProperty(AndworxConstants.LOG4J_LOG_DIR_KEY, logDirectory.getAbsolutePath());
    	URL url = new URL("platform:/plugin/" + PLUGIN_ID + "/log4j.properties");
   		PropertyConfigurator.configure(url);

        // Get the global logger to configure it
        Logger globalLogger = Logger.getGlobal();
        Logger rootLogger = Logger.getLogger("");
		String logPattern = logDirectory.getAbsolutePath() + "/java%g.log";
        // Suppress the logging output to the console
        Handler[] handlers = rootLogger.getHandlers();
        if ((handlers.length > 0) && handlers[0] instanceof ConsoleHandler) {
        	rootLogger.removeHandler(handlers[0]);
        }

        globalLogger.setLevel(Level.INFO);
        fileTxt = new FileHandler(logPattern, true);
        // Create a TXT formatter
        formatterTxt = new SimpleFormatter();
        fileTxt.setFormatter(formatterTxt);
        rootLogger.addHandler(fileTxt);
        logger.info("AndworxBuildPlugin started");
    	File databaseDirectory = new File(dataArea, ".andworx-database");
    	userFileCache = FileCache.getInstanceWithMultiProcessLocking(cacheDirectory);
    	// Create single instance of AndworxFactory
    	objectFactory = new AndworxFactory(
				databaseDirectory, 
    		    new EntityClassLoader() {

					@Override
					public Class<?> loadClass(String name) throws ClassNotFoundException {
						return context.getBundle().loadClass(name);
					}
				},
     		dataArea, 
    		userFileCache,
    		androidSdkHelper);
    	if (androidSdkValidator.isSdkLocationConfigured()) 
    		objectFactory.loadSdk(androidSdkHelper.getSdkLocation());
    	ContextInjectionFactory.inject(objectFactory, serviceContext);
    	serviceContext.set(AndworxFactory.class, objectFactory);
        Job job = new Job("Start bundle " + PLUGIN_ID) {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
		        try {
					initDesugarJar(userFileCache);
					initProguardFiles(context.getBundle());
					pluginHolder.setPlugin(AndworxBuildPlugin.this);
				} catch (IOException e) {
		        	logger.error("Error in " + getName(), e);
		        	return Status.CANCEL_STATUS;
				}
		        return Status.OK_STATUS;
	        }};
        IJobChangeListener listener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
				handleStartJobCompleted();
			}};
		job.addJobChangeListener(listener);
	    job.schedule();
        Job startPeristenceServiceJob = new Job("Starting " + PersistenceService.SERVICE_NAME) {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
	                objectFactory.startPersistenceService();
				} catch (Exception e) {
					logger.error(PersistenceService.SERVICE_NAME + " failed during initialization", e);
					return Status.CANCEL_STATUS;
				}
		        return Status.OK_STATUS;
	        }};
        IJobChangeListener persistenceStartListener = new JobChangeAdapter(){
			@Override
			public void done(IJobChangeEvent event) {
				if (event.getResult() == Status.OK_STATUS) {
					handleStartJobCompleted();
				} 
			}};
		startPeristenceServiceJob.addJobChangeListener(persistenceStartListener);
		startPeristenceServiceJob.setPriority(Job.BUILD);
		startPeristenceServiceJob.schedule();
   }

	/*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        if (colorRed != null) {
        	colorRed.dispose();
        	colorRed = null;
        }
        AndworxContext andworxFactory = AndworxFactory.instance();
        if (andworxFactory != null)
        	andworxFactory.getPersistenceService().stop();
        logger.info("AndworxBuildPlugin stopped");
		pluginHolder.setPlugin(null);
        super.stop(context);
    }

    /**
     * Initialize Proguard files
     * @param bundle The plugin bundle
     */
    protected void initProguardFiles(Bundle bundle) {
    	// Copy Proguard generation text files to File Manager container
    	// which is achieved just by retreiving them from the bundle
		for (String filename: ProguardFile.TEXT_FILENAMES) {
			String filePath = FileManager.FILES_ROOT + "/" + filename;
			if (objectFactory.getBundleFile(filePath) == null) {
				doPrintError("Proguard", "Default files not initialized do to error");
			}
		}
	}

    /** 
     * Returns the global build console 
     */
    private MessageConsole getBuildConsole() {
        return buildConsole;
    }
    
    private void doPrintToConsole(String tag, Object... objects) {
        doPrintToStream(buildConsoleStream, tag, objects);
    }
    
    private void doPrintError(String tag, Object... objects) {
        doPrintToStream(buildConsoleErrorStream, tag, objects);
    }

    private void doPrintToStream(MessageConsoleStream stream, String tag,
            Object... objects) {
        String dateTag = AndworxPrintStream.getMessageTag(tag);
        for (Object obj : objects) {
            stream.print(dateTag);
            stream.print(" "); //$NON-NLS-1$
            if (obj instanceof String) {
                stream.println((String)obj);
            } else if (obj == null) {
                stream.println("(null)");  //$NON-NLS-1$
            } else {
                stream.println(obj.toString());
            }
        }
    }
    
    /** 
     * Cache desugar deploy jar which is contained in the Android SDK build library archive 
     */
    private void initDesugarJar(@Nullable FileCache cache) throws IOException {
    	FileResource fileResource = new FileResource() {

			@Override
			public String getFileName() {
				return AndworxConstants.DESUGAR_JAR;
			}

			@Override
			public URL asUrl() throws IOException {
				return DesugarProcessBuilder.class.getClassLoader().getResource(AndworxConstants.DESUGAR_JAR);
			}};
        CachedFile cachedFile = new CachedFile(FileCache.Command.EXTRACT_DESUGAR_JAR, fileResource);
        objectFactory.getFileManager().addFile(cachedFile);
    }

    private void handleStartJobCompleted() {
		synchronized (jobsMonitor) {
	    	if (jobsCounter.incrementAndGet() == START_JOBS_COUNT) {
	    		andworxStarted();
	    	}
		}
    }
    
    /** 
     * Perform operations conditional on the workbench having been started 
     */
    private void andworxStarted() {
    	// Set red color on error console font
    	Display display = Display.getDefault(); 
    	colorRed = new Color(display, 0xFF, 0x00, 0x00);

        // because this can be run, in some cases, by a non ui thread, and because
        // changing the console properties update the ui, we need to make this change
        // in the ui thread.
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                buildConsoleErrorStream.setColor(colorRed);
           }
        });
        showBuildConsole();
		if (objectFactory.getAndroidEnvironment().isValid())
	        eventBroker.post(AndworxEvents.ANDWORX_STARTED, new Object());
		else {
			AndroidSdkPreferences prefs = objectFactory.getAndroidSdkPreferences();
			prefs.addPropertyChangeListener(new IPropertyChangeListener() {

				@Override
				public void propertyChange(PropertyChangeEvent event) {
			        eventBroker.post(AndworxEvents.ANDWORX_STARTED, new Object());
				}});
			isStarted = true;
			eventBroker.post(AndworxEvents.INSTALL_SDK_REQUEST, prefs);
		}
     }

	/**
	 * Returns plugin singleton instance. Note calling thread may be blocked pending completion of plugin intializataion.
	 * @return AndworxBuildPlugin object
	 */
	public static AndworxBuildPlugin instance() {
		return pluginHolder.getPlugin();
	}
	
    /**
     * Prints one or more message to the build console.
     * @param tag The tag to be associated with the message. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printToConsole(String tag, Object... objects) {
        instance().doPrintToConsole(tag, objects);
     }

    /**
     * Prints one or more error message to the android console.
     * @param tag A tag to be associated with the message. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printErrorToConsole(String tag, Object... objects) {
    	instance().doPrintError(tag, objects);
        showBuildConsole();
    }

    /** Force the display of the android console */
    public static void showBuildConsole() {
        // first make sure the console is in the workbench
        EclipseUiHelper.showView(IConsoleConstants.ID_CONSOLE_VIEW, true);

        // Now make sure it's not docked.
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(
        		instance().getBuildConsole());
    }


}
