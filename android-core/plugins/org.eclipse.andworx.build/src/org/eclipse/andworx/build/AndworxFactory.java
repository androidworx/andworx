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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.andworx.BuildFactory;
import org.eclipse.andworx.DaggerFactory;
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
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.andworx.ddms.devices.DeviceMonitor;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.process.java.JavaQueuedProcessor;
import org.eclipse.andworx.project.AndroidConfiguration;
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
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
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

public class AndworxFactory implements BuildFactory {
	
    /** AndworxFactory instance retrieval blocks until singleton is completed */
    private static class AndworxFactoryHolder {
        private AndworxFactory staticFactory;
    	private volatile AndworxFactory dynamicFactory;

    	public void setObjectFactory(AndworxFactory staticFactory) {
			synchronized(this) {
				this.staticFactory = staticFactory;
				if (staticFactory == null)
					dynamicFactory = null;
			}
    	}
    	
    	@SuppressWarnings("unused")
		public AndworxFactory getObjectFactory() {
    		while (dynamicFactory == null) {
    			synchronized(this) {
    	    		if ((dynamicFactory == null) && (staticFactory != null)) {
    	    			dynamicFactory = staticFactory;
                        return dynamicFactory;
    	    		}
    			}
    			try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
    		}
    		// Will return null if thread is interrupted before pluin start() is called
    		return dynamicFactory;
    	}
    }

	//private static AndworxFactoryHolder singletonHolder = new AndworxFactoryHolder();

	private DaggerFactory daggerFactory;
    /** Android environment - never null */
	private AndroidEnvironment androidEnvironment;
	private final SdkTracker sdkTracker;
	private final Map<Class<?>, Object> objectMap;
	private final AndroidSdkPreferences androidSdkPreferences;
	private final Devices devices;
	private final DeviceMonitor deviceMonitor;
	
	AndworxFactory(
			File databaseDirectory, 
			EntityClassLoader entityClassLoader, 
			File dataArea, 
			FileCache userFileCache,
			AndroidSdkPreferences androidSdkPreferences) {
		//singletonHolder.setObjectFactory(this);
		this.androidSdkPreferences = androidSdkPreferences;
		daggerFactory = new DaggerFactory(databaseDirectory, entityClassLoader, dataArea, userFileCache);
		objectMap = new HashMap<>();
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
        devices = new Devices(this);
        deviceMonitor = new DeviceMonitor(devices);
	}

    /**
     * Loads an SDK and returns flag to indicate success.
     * <p/>If the SDK failed to load, it displays an error to the user.
     * @param sdkLocation the OS path to the SDK.
     */
	public boolean loadSdk(File sdkLocation) {
    	// AndworxBuildPlugin validates the SDK location and stores details
    	// Note the following call may block at start up if the plugin has not completed initialization
        SdkProfile sdkProfile = getSdkTracker().setCurrentSdk(sdkLocation.getAbsolutePath());
        return sdkProfile.isValid();
    }

    public void put(Class<?> clazz, Object object) {
		objectMap.put(clazz, object);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(Class<T> clazz) {
		return (T) objectMap.get(clazz);
	}
	
	public AndroidEnvironment getAndroidEnvironment() {
		return androidEnvironment;
	}

	public SdkTracker getSdkTracker() {
		return sdkTracker;
	}

	public AvdManager getAvdManager() {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getAvdManager();
	}
	
	private void checkSdkAvailable() throws AndworxException {
		if ((sdkTracker == null) || (sdkTracker.getSdkProfile() == null))
			throw new AndworxException(SdkProfile.SDK_NOT_AVAILABLE_ERROR);
	}

	public DeviceManager getDeviceManager() {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getDeviceManager();
	}

	/**
	 * Returns closest match Target Platform to given version
	 * @param targetHash Target platform version specified as a hash string
	 * @return IAndroidTarget object
	 */
	public IAndroidTarget getAvailableTarget(String targetHash) {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getAvailableTarget(targetHash);
	}

    public IAndroidTarget getAndroidTargetFor(AvdInfo avdInfo) {
		checkSdkAvailable();
		return sdkTracker.getSdkProfile().getAndroidTargetFor(avdInfo);
    }

    public AndroidSdkPreferences getAndroidSdkPreferences() {
    	return androidSdkPreferences;
    }
    
    public Devices getDevices() {
		return devices;
	}

	public DeviceMonitor getDeviceMonitor() {
		return deviceMonitor;
	}

	public ProjectProfile createProject(
    		String projectName, 
			ProjectProfile projectProfile, 
			AndroidConfigurationBuilder androidConfigurationBuilder) {
    	return daggerFactory.createProject(projectName, projectProfile, androidConfigurationBuilder);
    }
    
	/**
	 * Returns Project profile read from database
	 * @param projectName Eclipse project name
	 * @param projectLocation Project location on file system
	 * @return ProjectFile object
	 */
	public ProjectProfile getProjectProfile(String projectName, File projectLocation) {
		return  daggerFactory.getProjectProfile(projectName, projectLocation, androidEnvironment);
	}

	/**
	 * Returns Project configuration read from database
	 * @param projectName Eclipse project name
	 * @param projectLocation Project location on file system
	 * @return ProjectConfiguration object
	 */
	public ProjectConfiguration getProjectConfig(String projectName, File projectLocation) {
		ProjectProfile profile =  daggerFactory.getProjectProfile(projectName, projectLocation, androidEnvironment);
		ProjectConfiguration projectConfiguration = daggerFactory.getProjectConfig(profile, projectName, projectLocation, androidEnvironment);
		return projectConfiguration;
	}

	/**
	 * Returns Project configuration read from database for given profile
	 * @param projectName Eclipse project name
	 * @param projectLocation Project location on file system
	 * @return ProjectProfile object
	 */
	public ProjectConfiguration getProjectConfig(ProjectProfile profile, String projectName, File projectLocation) {
		ProjectConfiguration projectConfiguration = daggerFactory.getProjectConfig(profile, projectName, projectLocation, androidEnvironment);
		return projectConfiguration;
	}

	public Map<String, VariantContext> createVariantContextMap(AndworxProject andworxProject, ProjectConfiguration projectConfig) {
		return daggerFactory.createVariantContextMap(andworxProject, projectConfig, androidEnvironment);
	}

	/**
	 * Returns Android configuration read from specified Gradle build file
	 * @param gradleBuildFile Inpt file 
	 * @return AndroidConfigurationBuilder object
	 */
    public AndroidConfigurationBuilder getAndroidConfigBuilder(File gradleBuildFile) {
    	return daggerFactory.getAndroidConfigBuilder(gradleBuildFile, androidEnvironment);
    }

    public RenderscriptCompileTask getRenderscriptCompileTask(VariantContext variantScope) {
    	return daggerFactory.getRenderscriptCompileTask(variantScope, androidEnvironment);
    }

    public AidlCompileTask getAidlCompileTask(VariantContext variantScope) {
    	return daggerFactory.getAidlCompileTask(variantScope, androidEnvironment);
    }
    
    public AndroidBuilder getAndroidBuilder(VariantContext variantScope) {
     	return daggerFactory.getAndroidBuilder(variantScope, androidEnvironment);
    }

    public BuildToolInfo getBuildToolInfo(String buildToolVersion) {
    	AndroidSdkHandler androidSdkHandler = androidEnvironment.getAndroidSdkHandler();
    	BuildToolInfo buildToolInfo = androidSdkHandler.getBuildToolInfo(Revision.parseRevision(buildToolVersion), new FakeProgressIndicator());;
    	if (buildToolInfo == null)
    		return androidSdkHandler.getLatestBuildTool(new FakeProgressIndicator(), true);
       return buildToolInfo;
 
    }
    
    public ProjectState getProjectState(IProject project) {
    	return getProjectRegistry().getProjectState(project);
    }

	public IAndroidTarget getTarget(IProject project) {
		return getProjectRegistry().getTarget(project);
	}

	public Set<ProjectState> getMainProjectsFor(IProject project) {
		return getProjectRegistry().getMainProjectsFor(project);
	}

	@Override
	public ProjectRegistry getProjectRegistry() {
		return daggerFactory.getProjectRegistry();
	}
	
    /**
	 * Returns persistence service
	 * @return PersistenceService object
	 */
	@Override
	public PersistenceContext getPersistenceContext() {
		return daggerFactory.getPersistenceContext();
	}

	@Override
	public PersistenceService getPersistenceService() {
		return daggerFactory.getPersistenceService();
	}

	@Override
	public AndroidConfiguration getAndroidConfiguration() {
		return daggerFactory.getAndroidConfiguration();
	}

	/**
	 * Returns m2e Maven services provider
	 * @return MavenServices object
	 */
	@Override
	public MavenServices getMavenServices() {
		return daggerFactory.getMavenServices();
	}

	/**
	 * Returns file manager
	 * @return FileManager object
	 */
	@Override
	public FileManager getFileManager() {
		return daggerFactory.getFileManager();
	}

    @Override
    public BuildElementFactory getBuildElementFactory() {
    	return daggerFactory.getBuildElementFactory();
    }

    @Override
    public BuildHelper getBuildHelper() {
    	return daggerFactory.getBuildHelper();
    }

    @Override
    public ProjectBuilder getProjectBuilder(IJavaProject javaProject, ProjectProfile profile) {
    	return daggerFactory.getProjectBuilder(javaProject, profile);
    }
    
   @Override
    public JavaQueuedProcessor getJavaQueuedProcessor() {
    	return daggerFactory.getJavaQueuedProcessor();
    }
    
    @Override
    public TaskFactory getTaskFactory() {
    	return daggerFactory.getTaskFactory();
    }
    
    /**
     * Returns bundle file specified by path. 
     * The file manager takes care of extracting the bundle file and caching it on the file system.
     * @param filePath File path
     * @return File object or null if an error occurs
     */
	@Override
	public File getBundleFile(String filePath) {
		return daggerFactory.getBundleFile(filePath);
	}

	@Override
	public Executable getExecutable(PersistenceWork persistenceWork) {
		return daggerFactory.getExecutable(persistenceWork);
	}

    @Override
    public PreManifestMergeTask getPreManifestMergeTask(
    		VariantContext variantScope,
			File manifestOutputDir) {
    	return daggerFactory.getPreManifestMergeTask(variantScope, manifestOutputDir);
    }

    @Override
    public BuildConfigTask getBuildConfigTask(String manifestPackage, VariantContext variantScope) {
    	return daggerFactory.getBuildConfigTask(manifestPackage, variantScope);
    }
 
    @Override
    public ManifestMergerTask getManifestMergerTask(ManifestMergeHandler manifestMergeHandler) {
    	return daggerFactory.getManifestMergerTask(manifestMergeHandler);
    }

    @Override
    public MergeResourcesTask getMergeResourcesTask(VariantContext variantScope) {
    	return daggerFactory.getMergeResourcesTask(variantScope);
    }

    @Override
    public NonNamespacedLinkResourcesTask getNonNamespacedLinkResourcesTask(VariantContext variantScope) {
    	return daggerFactory.getNonNamespacedLinkResourcesTask(variantScope);
    }

    @Override
    public DesugarTask getDesugarTask(Pipeline pipeline, ProjectBuilder projectBuilder) {
    	return daggerFactory.getDesugarTask(pipeline, projectBuilder);
    }

    @Override
    public D8Task getD8Task(Pipeline pipeline, VariantContext variantScope) {
        return daggerFactory.getD8Task(pipeline, variantScope);
    }
     
    @Override
    public PackageApplicationTask getPackageApplicationTask(VariantContext variantScope) {
    	return daggerFactory.getPackageApplicationTask(variantScope);
    }
    
	void setAndroidEnvironment(AndroidEnvironment androidEnvironment) {
		this.androidEnvironment = androidEnvironment;
	}
	
	void startPersistenceService() {
		daggerFactory.startPersistenceService();
	}

    public static AndworxFactory instance() {
		//return singletonHolder.getObjectFactory();
    	return E4Workbench.getServiceContext().get(AndworxFactory.class);
	}





	
}
