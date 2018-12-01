package org.eclipse.andworx.build;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.eclipse.andmore.base.BaseContext;
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
import org.eclipse.andworx.task.ManifestMergeHandler;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.Pipeline;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;

import com.android.builder.core.AndroidBuilder;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;

import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classytask.Executable;

/**
 * Andworx object factory
 */
public interface AndworxContext extends BaseContext{

	/**
	 * Loads an SDK and returns flag to indicate success.
	 * <p/>If the SDK failed to load, it displays an error to the user.
	 * @param sdkLocation the OS path to the SDK.
	 */
	boolean loadSdk(File sdkLocation);

	void put(Class<?> clazz, Object object);

	<T> T get(Class<T> clazz);

	AndroidEnvironment getAndroidEnvironment();

	SdkTracker getSdkTracker();

	AvdManager getAvdManager();

	DeviceManager getDeviceManager();

	/**
	 * Returns closest match Target Platform to given version
	 * @param targetHash Target platform version specified as a hash string
	 * @return IAndroidTarget object
	 */
	IAndroidTarget getAvailableTarget(String targetHash);

	IAndroidTarget getAndroidTargetFor(AvdInfo avdInfo);

	AndroidSdkPreferences getAndroidSdkPreferences();

	Devices getDevices();

	DeviceMonitor getDeviceMonitor();

	ProjectProfile createProject(String projectName, ProjectProfile projectProfile,
			AndroidConfigurationBuilder androidConfigurationBuilder);

	/**
	 * Returns Project profile read from database
	 * @param projectName Eclipse project name
	 * @param projectLocation Project location on file system
	 * @return ProjectFile object
	 */
	ProjectProfile getProjectProfile(String projectName, File projectLocation);

	/**
	 * Returns Project configuration read from database
	 * @param projectName Eclipse project name
	 * @param projectLocation Project location on file system
	 * @return ProjectConfiguration object
	 */
	ProjectConfiguration getProjectConfig(String projectName, File projectLocation);

	/**
	 * Returns Project configuration read from database for given profile
	 * @param projectName Eclipse project name
	 * @param projectLocation Project location on file system
	 * @return ProjectProfile object
	 */
	ProjectConfiguration getProjectConfig(ProjectProfile profile, String projectName, File projectLocation);

	Map<String, VariantContext> createVariantContextMap(AndworxProject andworxProject,
			ProjectConfiguration projectConfig);

	/**
	 * Returns Android configuration read from specified Gradle build file
	 * @param gradleBuildFile Inpt file 
	 * @return AndroidConfigurationBuilder object
	 */
	AndroidConfigurationBuilder getAndroidConfigBuilder(File gradleBuildFile);

	RenderscriptCompileTask getRenderscriptCompileTask(VariantContext variantScope);

	AidlCompileTask getAidlCompileTask(VariantContext variantScope);

	AndroidBuilder getAndroidBuilder(VariantContext variantScope);

	BuildToolInfo getBuildToolInfo(String buildToolVersion);

	ProjectState getProjectState(IProject project);

	IAndroidTarget getTarget(IProject project);

	Set<ProjectState> getMainProjectsFor(IProject project);

	ProjectRegistry getProjectRegistry();

	/**
	 * Returns persistence service
	 * @return PersistenceService object
	 */
	PersistenceContext getPersistenceContext();
	
	SecurityController getSecurityController();

	PersistenceService getPersistenceService();

	AndroidConfiguration getAndroidConfiguration();

	/**
	 * Returns m2e Maven services provider
	 * @return MavenServices object
	 */
	MavenServices getMavenServices();

	/**
	 * Returns file manager
	 * @return FileManager object
	 */
	FileManager getFileManager();

	BuildElementFactory getBuildElementFactory();

	BuildHelper getBuildHelper();

	ProjectBuilder getProjectBuilder(IJavaProject javaProject, ProjectProfile profile);

	JavaQueuedProcessor getJavaQueuedProcessor();

	TaskFactory getTaskFactory();

	/**
	 * Returns bundle file specified by path. 
	 * The file manager takes care of extracting the bundle file and caching it on the file system.
	 * @param filePath File path
	 * @return File object or null if an error occurs
	 */
	File getBundleFile(String filePath);

	Executable getExecutable(PersistenceWork persistenceWork);

	PreManifestMergeTask getPreManifestMergeTask(VariantContext variantScope, File manifestOutputDir);

	BuildConfigTask getBuildConfigTask(String manifestPackage, VariantContext variantScope);

	ManifestMergerTask getManifestMergerTask(ManifestMergeHandler manifestMergeHandler);

	MergeResourcesTask getMergeResourcesTask(VariantContext variantScope);

	NonNamespacedLinkResourcesTask getNonNamespacedLinkResourcesTask(VariantContext variantScope);

	DesugarTask getDesugarTask(Pipeline pipeline, ProjectBuilder projectBuilder);

	D8Task getD8Task(Pipeline pipeline, VariantContext variantScope);

	PackageApplicationTask getPackageApplicationTask(VariantContext variantScope);

}