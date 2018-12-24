package org.eclipse.andworx.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.andmore.base.AndworxJob;
import org.eclipse.andmore.base.BaseContext;
import org.eclipse.andmore.base.BasePlugin;
import org.eclipse.andmore.base.JavaProjectHelper;
import org.eclipse.andmore.base.resources.PluginResourceRegistry;
import org.eclipse.andworx.DaggerFactory;
import org.eclipse.andworx.build.AndworxBuildPlugin;
import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.BuildConsole;
import org.eclipse.andworx.build.SdkTracker;
import org.eclipse.andworx.build.task.AidlCompileTask;
import org.eclipse.andworx.build.task.BuildConfigTask;
import org.eclipse.andworx.build.task.D8Task;
import org.eclipse.andworx.build.task.DesugarTask;
import org.eclipse.andworx.build.task.ManifestMergerTask;
import org.eclipse.andworx.build.task.MergeResourcesTask;
import org.eclipse.andworx.build.task.NonNamespacedLinkResourcesTask;
import org.eclipse.andworx.build.task.PackageApplicationTask;
import org.eclipse.andworx.build.task.PreManifestMergeTask;
import org.eclipse.andworx.build.task.RenderscriptCompileTask;
import org.eclipse.andworx.config.SecurityController;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.ddms.devices.DeviceMonitor;
import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.CacheManager;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.polyglot.AndworxBuildParser;
import org.eclipse.andworx.process.java.JavaQueuedProcessor;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.AndroidDigest;
import org.eclipse.andworx.project.AndworxParserContext;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.ProjectConfiguration;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.sdk.AndroidSdkPreferences;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.andworx.task.ManifestMergeHandler;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.Pipeline;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jdt.core.IJavaProject;

import com.android.builder.core.AndroidBuilder;
import com.android.builder.utils.FileCache;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;

import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;
import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classytask.Executable;

public class TestAndworxContext implements AndworxContext {

	private static BaseContext baseContext;
	static {
		baseContext = BasePlugin.getBaseContext();
	}
	

	private final IEclipseContext eclipseContext;
	private final DaggerFactory daggerFactory;
	private final AndroidSdkPreferences androidSdkPreferences;
	private final SdkTracker sdkTracker;
	private AndroidEnvironment androidEnvironment;
	private final TestMavenServices testMavenServices;
	private final Map<Class<?>, Object> objectMap;
	private BuildConsole testBuildConsole = new BuildConsole() {

		@Override
		public void logAndPrintError(Throwable exception, String tag, String format, Object... args) {
	        String message = String.format(format, args);
	        System.err.print(message);
	        if (exception != null)
	        	exception.printStackTrace();
		}}; 

	public TestAndworxContext(			
			File databaseDirectory, 
			EntityClassLoader entityClassLoader, 
			File dataArea, 
			AndroidSdkPreferences androidSdkPreferences) {
		this.androidSdkPreferences = androidSdkPreferences;
		testMavenServices = new TestMavenServices();
		objectMap = new HashMap<>();
		eclipseContext = baseContext.getEclipseContext();
        IEventBroker eventBroker = (IEventBroker) eclipseContext.get(IEventBroker.class.getName());
		File cacheDirectory = new File(dataArea, "cache");
		FileCache userFileCache = FileCache.getInstanceWithMultiProcessLocking(cacheDirectory);
		daggerFactory = new DaggerFactory(eventBroker, null, entityClassLoader, dataArea, userFileCache);
		// Throw exception if any error is logged
		ILogger errorLogger = new ILogger() {

			@Override
			public void error(Throwable t, String msgFormat, Object... args) {
				throw new AndworxException(String.format(msgFormat, args), t);
			}

			@Override
			public void warning(String msgFormat, Object... args) {
			}

			@Override
			public void info(String msgFormat, Object... args) {
			}

			@Override
			public void verbose(String msgFormat, Object... args) {
			}};
		// Create default Android environment. This will be updated as each SDK is loaded.
		androidEnvironment = new AndroidEnvironment(errorLogger) {
			public  AndroidSdkHandler getAndroidSdkHandler() {
				if (getAndroidTargets().isEmpty())
					throw new AndworxException("No targets available");
				return super.getAndroidSdkHandler();
			}

			public IAndroidTarget getAvailableTarget(String targetHash) {
				if (getAndroidTargets().isEmpty())
					throw new AndworxException("No targets available");
				return super.getAvailableTarget(targetHash)	;
			}
		};
		sdkTracker = new SdkTracker(this);
		AndworxFactory.setAndworxContext(this);
	}
	
	@Override
	public IEclipseContext getEclipseContext() {
		return eclipseContext;
	}

	@Override
	public PluginResourceRegistry getPluginResourceRegistry() {
		return baseContext.getPluginResourceRegistry();
	}

	@Override
	public JavaProjectHelper getJavaProjectHelper() {
		return baseContext.getJavaProjectHelper();
	}
	
	@Override
	public AndworxJob getAndworxJob(String name, IJobFunction jobFunction) {
		return baseContext.getAndworxJob(name, jobFunction);
	}
	
	@Override
	public AndroidSdkHandler getAndroidSdkHandler(File localPath) {
		return baseContext.getAndroidSdkHandler(localPath);
	}
	
	@Override
	public BuildConsole getBuildConsole() {
		return testBuildConsole;
	}
	
   /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#loadSdk(java.io.File)
	 */
	@Override
	public boolean loadSdk(File sdkLocation) {
    	// AndworxBuildPlugin validates the SDK location and stores details
    	// Note the following call may block at start up if the plugin has not completed initialization
        SdkProfile sdkProfile = getSdkTracker().setCurrentSdk(sdkLocation.getAbsolutePath());
        return sdkProfile.isValid();
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#put(java.lang.Class, java.lang.Object)
	 */
    @Override
	public void put(Class<?> clazz, Object object) {
		objectMap.put(clazz, object);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#get(java.lang.Class)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(Class<T> clazz) {
		return (T) objectMap.get(clazz);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAndroidEnvironment()
	 */
	@Override
	public AndroidEnvironment getAndroidEnvironment() {
		SdkProfile sdkProfile = sdkTracker.getSdkProfile();
		return sdkProfile != null ? sdkProfile : androidEnvironment;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getSdkTracker()
	 */
	@Override
	public SdkTracker getSdkTracker() {
		return sdkTracker;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAvdManager()
	 */
	@Override
	public AvdManager getAvdManager() {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getAvdManager();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getDeviceManager()
	 */
	@Override
	public DeviceManager getDeviceManager() {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getDeviceManager();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAvailableTarget(java.lang.String)
	 */
	@Override
	public IAndroidTarget getAvailableTarget(String targetHash) {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getAvailableTarget(targetHash);
	}

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAndroidTargetFor(com.android.sdklib.internal.avd.AvdInfo)
	 */
    @Override
	public IAndroidTarget getAndroidTargetFor(AvdInfo avdInfo) {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getAndroidTargetFor(avdInfo);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAndroidSdkPreferences()
	 */
    @Override
	public AndroidSdkPreferences getAndroidSdkPreferences() {
    	return androidSdkPreferences;
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getDevices()
	 */
    @Override
	public Devices getDevices() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getDeviceMonitor()
	 */
	@Override
	public DeviceMonitor getDeviceMonitor() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#createProject(java.lang.String, org.eclipse.andworx.project.ProjectProfile, org.eclipse.andworx.polyglot.AndroidConfigurationBuilder)
	 */
	@Override
	public ProjectProfile createProject(
    		String projectName, 
			ProjectProfile projectProfile, 
			AndroidDigest androidDigest) {
    	return daggerFactory.createProject(projectName, projectProfile, androidDigest);
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getProjectProfile(java.lang.String, java.io.File)
	 */
	@Override
	public ProjectProfile getProjectProfile(String projectName, File projectLocation) {
		return  daggerFactory.getProjectProfile(projectName, projectLocation, androidEnvironment);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getProjectConfig(java.lang.String, java.io.File)
	 */
	@Override
	public ProjectConfiguration getProjectConfig(String projectName, File projectLocation) {
		ProjectProfile profile =  daggerFactory.getProjectProfile(projectName, projectLocation, androidEnvironment);
		ProjectConfiguration projectConfiguration = daggerFactory.getProjectConfig(profile, projectName, projectLocation, androidEnvironment);
		return projectConfiguration;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getProjectConfig(org.eclipse.andworx.project.ProjectProfile, java.lang.String, java.io.File)
	 */
	@Override
	public ProjectConfiguration getProjectConfig(ProjectProfile profile, String projectName, File projectLocation) {
		ProjectConfiguration projectConfiguration = daggerFactory.getProjectConfig(profile, projectName, projectLocation, androidEnvironment);
		return projectConfiguration;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#createVariantContextMap(org.eclipse.andworx.project.AndworxProject, org.eclipse.andworx.project.ProjectConfiguration)
	 */
	@Override
	public Map<String, VariantContext> createVariantContextMap(AndworxProject andworxProject, ProjectConfiguration projectConfig) {
		return daggerFactory.createVariantContextMap(andworxProject, projectConfig, androidEnvironment);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAndroidConfigBuilder(java.io.File)
	 */
    @Override
	public AndroidConfigurationBuilder getAndroidConfigBuilder() {
    	return daggerFactory.getAndroidConfigBuilder(androidEnvironment);
    }

    @Override
    public AndworxBuildParser getAndworxBuildParser(AndworxParserContext context) {
    	return new AndworxBuildParser(context, getAndroidConfigBuilder());
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getRenderscriptCompileTask(org.eclipse.andworx.context.VariantContext)
	 */
    @Override
	public RenderscriptCompileTask getRenderscriptCompileTask(VariantContext variantScope) {
    	return daggerFactory.getRenderscriptCompileTask(variantScope, androidEnvironment);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAidlCompileTask(org.eclipse.andworx.context.VariantContext)
	 */
    @Override
	public AidlCompileTask getAidlCompileTask(VariantContext variantScope) {
    	return daggerFactory.getAidlCompileTask(variantScope, androidEnvironment);
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAndroidBuilder(org.eclipse.andworx.context.VariantContext)
	 */
    @Override
	public AndroidBuilder getAndroidBuilder(VariantContext variantScope) {
     	return daggerFactory.getAndroidBuilder(variantScope, androidEnvironment);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getBuildToolInfo(java.lang.String)
	 */
    @Override
	public BuildToolInfo getBuildToolInfo(String buildToolVersion) {
    	AndroidSdkHandler androidSdkHandler = androidEnvironment.getAndroidSdkHandler();
    	BuildToolInfo buildToolInfo = androidSdkHandler.getBuildToolInfo(Revision.parseRevision(buildToolVersion), new FakeProgressIndicator());;
    	if (buildToolInfo == null)
    		return androidSdkHandler.getLatestBuildTool(new FakeProgressIndicator(), true);
       return buildToolInfo;
 
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getProjectState(org.eclipse.core.resources.IProject)
	 */
    @Override
	public ProjectState getProjectState(IProject project) {
    	return getProjectRegistry().getProjectState(project);
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getTarget(org.eclipse.core.resources.IProject)
	 */
	@Override
	public IAndroidTarget getTarget(IProject project) {
		return getProjectRegistry().getTarget(project);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getMainProjectsFor(org.eclipse.core.resources.IProject)
	 */
	@Override
	public Set<ProjectState> getMainProjectsFor(IProject project) {
		return getProjectRegistry().getMainProjectsFor(project);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getProjectRegistry()
	 */
	@Override
	public ProjectRegistry getProjectRegistry() {
		return daggerFactory.getProjectRegistry();
	}
	
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getPersistenceContext()
	 */
	@Override
	public PersistenceContext getPersistenceContext() {
		return daggerFactory.getPersistenceContext();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getPersistenceService()
	 */
	@Override
	public PersistenceService getPersistenceService() {
		return daggerFactory.getPersistenceService();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getAndroidConfiguration()
	 */
	@Override
	public AndroidConfiguration getAndroidConfiguration() {
		return daggerFactory.getAndroidConfiguration();
	}

	@Override
	public SecurityController getSecurityController() {
		return daggerFactory.getSecurityController();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getMavenServices()
	 */
	@Override
	public MavenServices getMavenServices() {
		return testMavenServices;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getFileManager()
	 */
	@Override
	public FileManager getFileManager() {
		return daggerFactory.getFileManager();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getFileManager()
	 */
	@Override
	public CacheManager getCacheManager() {
		return daggerFactory.getCacheManager();
	}

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getBuildElementFactory()
	 */
    @Override
    public BuildElementFactory getBuildElementFactory() {
    	return daggerFactory.getBuildElementFactory();
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getBuildHelper()
	 */
    @Override
    public BuildHelper getBuildHelper() {
    	return daggerFactory.getBuildHelper();
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getProjectBuilder(org.eclipse.jdt.core.IJavaProject, org.eclipse.andworx.project.ProjectProfile)
	 */
    @Override
    public ProjectBuilder getProjectBuilder(IJavaProject javaProject, ProjectProfile profile) {
    	return daggerFactory.getProjectBuilder(javaProject, profile);
    }
    
   /* (non-Javadoc)
    * @see org.eclipse.andworx.build.AndworxContext#getJavaQueuedProcessor()
    */
    @Override
    public JavaQueuedProcessor getJavaQueuedProcessor() {
    	return daggerFactory.getJavaQueuedProcessor();
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getTaskFactory()
	 */
    @Override
    public TaskFactory getTaskFactory() {
    	return daggerFactory.getTaskFactory();
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getBundleFile(java.lang.String)
	 */
	@Override
	public File getBundleFile(String filePath) {
		return daggerFactory.getBundleFile(filePath);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getExecutable(au.com.cybersearch2.classyjpa.entity.PersistenceWork)
	 */
	@Override
	public Executable getExecutable(PersistenceWork persistenceWork) {
		return daggerFactory.getExecutable(persistenceWork);
	}

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getPreManifestMergeTask(org.eclipse.andworx.context.VariantContext, java.io.File)
	 */
    @Override
    public PreManifestMergeTask getPreManifestMergeTask(
    		VariantContext variantScope,
			File manifestOutputDir) {
    	return daggerFactory.getPreManifestMergeTask(variantScope, manifestOutputDir);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getBuildConfigTask(java.lang.String, org.eclipse.andworx.context.VariantContext)
	 */
    @Override
    public BuildConfigTask getBuildConfigTask(String manifestPackage, VariantContext variantScope) {
    	return daggerFactory.getBuildConfigTask(manifestPackage, variantScope);
    }
 
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getManifestMergerTask(org.eclipse.andworx.task.ManifestMergeHandler)
	 */
    @Override
    public ManifestMergerTask getManifestMergerTask(ManifestMergeHandler manifestMergeHandler) {
    	return daggerFactory.getManifestMergerTask(manifestMergeHandler);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getMergeResourcesTask(org.eclipse.andworx.context.VariantContext)
	 */
    @Override
    public MergeResourcesTask getMergeResourcesTask(VariantContext variantScope) {
    	return daggerFactory.getMergeResourcesTask(variantScope);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getNonNamespacedLinkResourcesTask(org.eclipse.andworx.context.VariantContext)
	 */
    @Override
    public NonNamespacedLinkResourcesTask getNonNamespacedLinkResourcesTask(VariantContext variantScope) {
    	return daggerFactory.getNonNamespacedLinkResourcesTask(variantScope);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getDesugarTask(org.eclipse.andworx.transform.Pipeline, org.eclipse.andworx.helper.ProjectBuilder)
	 */
    @Override
    public DesugarTask getDesugarTask(Pipeline pipeline, ProjectBuilder projectBuilder) {
    	return daggerFactory.getDesugarTask(pipeline, projectBuilder);
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getD8Task(org.eclipse.andworx.transform.Pipeline, org.eclipse.andworx.context.VariantContext)
	 */
    @Override
    public D8Task getD8Task(Pipeline pipeline, VariantContext variantScope) {
        return daggerFactory.getD8Task(pipeline, variantScope);
    }
     
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.build.AndworxContext#getPackageApplicationTask(org.eclipse.andworx.context.VariantContext)
	 */
    @Override
    public PackageApplicationTask getPackageApplicationTask(AndworxProject andworxProject, VariantContext variantScope) {
    	return daggerFactory.getPackageApplicationTask(andworxProject, variantScope);
    }
    
	public void startPersistenceService() {
		daggerFactory.startPersistenceService();
	}

	private void checkSdkAvailable() throws AndworxException {
		if ((sdkTracker == null) || (sdkTracker.getSdkProfile() == null))
			throw new AndworxException(SdkProfile.SDK_NOT_AVAILABLE_ERROR);
	}
}
