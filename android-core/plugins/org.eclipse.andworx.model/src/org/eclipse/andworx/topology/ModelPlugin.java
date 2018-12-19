package org.eclipse.andworx.topology;

import java.io.File;

import org.eclipse.andmore.base.BaseContext;
import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.SqliteEnvironment;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import au.com.cybersearch2.classyapp.ResourceEnvironment;
import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;

/**
 * The activator class controls the plug-in life cycle
 */
public class ModelPlugin extends AbstractUIPlugin {

	/** The plug-in ID **/
	public static final String PLUGIN_ID = "org.eclipse.andworx.model"; //$NON-NLS-1$

	/** The shared instance **/
	private static ModelPlugin plugin;

	/** External ModelFactory object - only for unit testing */
	private static ModelFactory externalModelFactory;
	private static BaseContext baseContext;
	private static SdkLogger logger = SdkLogger.getLogger(ModelPlugin.class.getName());
	
	static {
		baseContext = BasePlugin.getBaseContext();
	}

	/** Event broker service */
    private IEventBroker eventBroker;
    /** Workspace location in which to locate the Database */
    private File dataArea;
    private ModelFactory modelFactory;

	/**
	 * The constructor
	 */
	public ModelPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		logger.info("Starting Model Plugin");
        IEclipseContext eclipseContext = baseContext.getEclipseContext(); 
    	eventBroker = (IEventBroker) eclipseContext.get(IEventBroker.class.getName());
		IPath pluginDataPath = null;
		try {
			pluginDataPath = getStateLocation();
		} catch (IllegalStateException e) {
			// the system is running with no data area (-data @none),
			// or a data area has not been set yet
			throw new AndworxException("Plugin data area not configured", e);
		}
		dataArea = pluginDataPath.makeAbsolute().toFile();
		modelFactory = createObjectFactory(eventBroker, dataArea, new EntityClassLoader() {

			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				return context.getBundle().loadClass(name);
			}
		});
		eclipseContext.set(ModelFactory.class, modelFactory);
        Job startPeristenceServiceJob = new Job("Starting " + PersistenceService.SERVICE_NAME) {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					modelFactory.startPersistenceService();
				} catch (Exception e) {
					logger.error(e, PersistenceService.SERVICE_NAME + " failed during initialization");
					return Status.CANCEL_STATUS;
				}
		        return Status.OK_STATUS;
	        }};
		startPeristenceServiceJob.setPriority(Job.BUILD);
		startPeristenceServiceJob.schedule();
	}


	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	private ModelFactory createObjectFactory(IEventBroker eventBroker2, File dataArea, EntityClassLoader classLoader) {
		FileManager fileManager = new FileManager(dataArea, AndworxConstants.ANDWORX_BUILD_VERSION);
    	File databaseDirectory = new File(dataArea, ".model-database");
    	ResourceEnvironment resourceEnvironment = new SqliteEnvironment(
				ModelPlugin.PLUGIN_ID,
				"META-INF", 
				databaseDirectory, 
				classLoader,
				fileManager);
		return new DaggerFactory(
				        eventBroker,
						resourceEnvironment);
	}

	public static ModelFactory getModelFactory() {
		ModelFactory modelFactory;
		// Support unit testing for which Eclipse context is not available
		if (isOsgiPlatform()) {
			IEclipseContext eclipseContext = baseContext.getEclipseContext();
			modelFactory = eclipseContext.get(ModelFactory.class);
		} else {
			modelFactory = externalModelFactory;
		}
		return modelFactory;
	}
	
	public static void setModelFactory(ModelFactory modelFactory) {
		// Support unit testing for which Eclipse context is not available
		if (!isOsgiPlatform()) {
			externalModelFactory = modelFactory;
		} else
			throw new UnsupportedOperationException("Context cannot be changed");
	}
	
	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static ModelPlugin instance() {
		return plugin;
	}

    private static boolean isOsgiPlatform() {
    	return (BasePlugin.instance() != null);
    }
	
}
