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
import java.util.Map;

import javax.inject.Singleton;

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
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.helper.BuildElementFactory;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.jpa.PersistenceRunner;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.maven.MavenServices;
import org.eclipse.andworx.polyglot.AndroidConfigurationBuilder;
import org.eclipse.andworx.process.java.JavaQueuedProcessor;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.project.AndworxProject;
import org.eclipse.andworx.project.ProjectConfiguration;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.registry.ProjectRegistry;
import org.eclipse.andworx.task.ManifestMergeHandler;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.Pipeline;
import org.eclipse.andworx.transform.TransformAgent;
import org.eclipse.jdt.core.IJavaProject;

import com.android.builder.core.AndroidBuilder;
import com.android.builder.utils.FileCache;

import au.com.cybersearch2.classyjpa.entity.EntityClassLoader;
import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceAdmin;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classytask.Executable;
import dagger.Component;
import dagger.Subcomponent;

/**
 * Uses Dagger component to manufacture objects
 */
public class DaggerFactory implements BuildFactory {

	/** The component specification. AndworxModule provides objects. */
	@Singleton
    @Component(modules = AndworxModule.class)  
    static interface ApplicationComponent {
		ProjectRegistry projectRegistry();
		/** Returns the PersistenceContext singleton */
        PersistenceContext persistenceContext();
		/** Returns the PersistenceService singleton */
        PersistenceService persistenceService();
		/** Returns the AndroidConfiguration singleton */
		AndroidConfiguration androidConfiguration();
		SecurityController securityController();
		/** Returns the MavenServices singleton */
		MavenServices mavenServices();
		FileManager fileManager();
		BuildElementFactory buildElementFactory();
		BuildHelper buildHelper();
		JavaQueuedProcessor javaQueuedProcessor();
		TaskFactory taskFactory();
		TransformAgent transformAgent();
        /** Returns a PersistenceWorkSubcontext instance.  PersistenceWorkModule provides additional objects. */
        PersistenceWorkSubcontext plus(PersistenceWorkModule persistenceWorkModule);
        AndroidBuilderSubcontext plus(AndroidBuilderModule androidBuilderModule);
        ProjectBuilderSubcontext plus(ProjectBuilderModule projectBuilderModule);
        BundleFileSubcontext plus(BundleFileModule bundleFileModule);
        CreateProjectSubcontext plus(CreateProjectModule createProjectModule);
        ProjectSubcontext plus(ProjectModule projectModule);
        ProjectConfigSubcontext plus(ProjectConfigModule projectConfigModule);
        VariantContextSubcontext plus(VariantContextModule variantContextModule);
        ConfigBuilderSubcontext plus(ConfigBuilderModule configBuilderModule);
        PreManifestMergeSubcontext plus(PreManifestMergeModule preManifestMergeModule);
        BuildConfigSubcontext plus(BuildConfigModule buildConfigModule);
        RenderscriptCompileSubcontext plus(RenderscriptCompileModule renderscriptCompileModule, AndroidBuilderModule androidBuilderModule);
        AidlCompileSubcontext plus(AidlCompileModule aidlCompileModule, AndroidBuilderModule androidBuilderModule);
        ManifestMergerSubcontext plus(ManifestMergerModule mnifestMergerModule);
        MergeResourcesSubcontext plus(MergeResourcesModule mergedResourcesModule, Aapt2ExecutorModule aapt2ExecutorModule);
        LinkResourcesSubcontext plus(LinkResourcesModule linkResourcesModule, Aapt2ExecutorModule aapt2ExecutorModule);
        DesugarSubcontext plus(DesugarModule dsugarModule);
        D8Subcontext plus(D8Module D8Module);
        PackageApplicationSubcontext plus(PackageApplicationModule packageApplicationModule);
    }

	/** Sub component interface for executing persistence tasks */
    @Singleton
    @Subcomponent(modules = PersistenceWorkModule.class)
    static interface PersistenceWorkSubcontext {  
    	/** Returns object to notify result */
        Executable executable();
    }
 
	/** Sub component interface for accessing bundle files */
    @Singleton
    @Subcomponent(modules = BundleFileModule.class)
    static interface BundleFileSubcontext {
    	File file();

    }

	/** Sub component interface for creating project profile objects */
    @Singleton
    @Subcomponent(modules = CreateProjectModule.class)
    static interface CreateProjectSubcontext {
    	ProjectProfile projectProfile();
    }
    
	/** Sub component interface for creating project profile objects */
    @Singleton
    @Subcomponent(modules = ProjectModule.class)
    static interface ProjectSubcontext {
    	ProjectProfile projectProfile();
    }
    
	/** Sub component interface for creating project configuration objects */
    @Singleton
    @Subcomponent(modules = ProjectConfigModule.class)
    static interface ProjectConfigSubcontext {
    	ProjectConfiguration projectConfig();
    }
    
    /** Sub component interface for variant context creation */
    @Singleton
    @Subcomponent(modules = VariantContextModule.class)
    static interface VariantContextSubcontext {
    	Map<String, VariantContext> variantContextMap();
    }

    /** Sub component interface for android configuration builder creation */
    @Singleton
    @Subcomponent(modules = ConfigBuilderModule.class)
    static interface ConfigBuilderSubcontext {
    	AndroidConfigurationBuilder androidConfigurationBuilder();
    }

    /** Sub component interface for project builder creation */
    @Singleton
    @Subcomponent(modules = ProjectBuilderModule.class)
   static interface ProjectBuilderSubcontext {
    	ProjectBuilder projectBuilder();
    }
    
    /** Sub component interface for pre manifest merge creation */
    @Singleton
    @Subcomponent(modules = PreManifestMergeModule.class)
    static interface PreManifestMergeSubcontext {
    	PreManifestMergeTask preManifestMergeTask();
    }

    /** Sub component interface for build config creation */
    @Singleton
    @Subcomponent(modules = BuildConfigModule.class)
    static interface BuildConfigSubcontext {
    	BuildConfigTask buildConfigTask();
    }
 
    /** Sub component interface for renderscript compile creation */
    @Singleton
    @Subcomponent(modules = { RenderscriptCompileModule.class, AndroidBuilderModule.class } )
    static interface RenderscriptCompileSubcontext {
    	RenderscriptCompileTask renderscriptCompileTask();
    }
    
    /** Sub component interface for aidl compile creation */
    @Singleton
    @Subcomponent(modules = { AidlCompileModule.class, AndroidBuilderModule.class } )
    static interface AidlCompileSubcontext {
    	AidlCompileTask aidlCompileTask();
    }
    
    /** Sub component interface for manifest merger creation */
    @Singleton
    @Subcomponent(modules = ManifestMergerModule.class)
    static interface ManifestMergerSubcontext {
    	ManifestMergerTask manifestMergerTask();
    }
 
    /** Sub component interface for merge resources creation */
    @Singleton
    @Subcomponent(modules = { MergeResourcesModule.class, Aapt2ExecutorModule.class })
    static interface MergeResourcesSubcontext {
    	MergeResourcesTask mergeResourcesTask();
    }
    
    /** Sub component interface for link resources creation */
    @Singleton
    @Subcomponent(modules = { LinkResourcesModule.class, Aapt2ExecutorModule.class })
    static interface LinkResourcesSubcontext {
    	NonNamespacedLinkResourcesTask linkResourcesTask();
    }

    /** Sub component interface for Desugar creation */
    @Singleton
    @Subcomponent(modules = DesugarModule.class)
    static interface DesugarSubcontext {
    	DesugarTask desugarTask();
    }
    
    /** Sub component interface for D8 creation */
    @Singleton
    @Subcomponent(modules = D8Module.class)
    static interface D8Subcontext {
    	D8Task d8Task();
    }
    
    /** Sub component interface for package APK creation */
    @Singleton
    @Subcomponent(modules = PackageApplicationModule.class)
    static interface PackageApplicationSubcontext {
    	PackageApplicationTask packageApplicationTask();
    }
 
    /** Sub component interface for android builder creation */
    @Singleton
    @Subcomponent(modules = AndroidBuilderModule.class)
    static interface AndroidBuilderSubcontext {
    	AndroidBuilder androidBuilder();
    }
    
    static private SdkLogger logger = SdkLogger.getLogger(DaggerFactory.class.getName());
    
    /** The applicaton component */
    protected ApplicationComponent component;
 
    /**
     * Construct DaggerFactory object
     * @param resourceEnvironment Resources adapter for Sqlite database on Andworx
     */ //ResourceEnvironment resourceEnvironment
    public DaggerFactory(File databaseDirectory, EntityClassLoader entityClassLoader, File dataArea, FileCache userFileCache) { 
    	// The component builder is generated by the Dagger annotation processor.
    	// The builder class name consists of "Dagger" + this class name +"_ApplicationComponent"
        component = 
                DaggerDaggerFactory_ApplicationComponent.builder()
                .andworxModule(new AndworxModule(databaseDirectory, entityClassLoader, dataArea, userFileCache, logger))
                .build();
    }

    public ProjectProfile createProject(
    		String projectName, 
			ProjectProfile projectProfile, 
			AndroidConfigurationBuilder androidConfigurationBuilder) {
    	CreateProjectModule createProjectModule = new CreateProjectModule(projectName, projectProfile, androidConfigurationBuilder);
    	return component.plus(createProjectModule).projectProfile();
    }
    
    public ProjectProfile getProjectProfile(String projectName, File projectLocation, AndroidEnvironment androidEnvironment) {
    	ProjectModule projectModule = new ProjectModule(projectName, projectLocation);
    	return component.plus(projectModule).projectProfile();
    }
    
    public ProjectConfiguration getProjectConfig(ProjectProfile projectProfile, String projectName, File projectLocation, AndroidEnvironment androidEnvironment) {
    	ProjectConfigModule projectConfigModule = new ProjectConfigModule(projectProfile, projectName, projectLocation, androidEnvironment);
    	return component.plus(projectConfigModule).projectConfig();
    }
 
	public Map<String, VariantContext> createVariantContextMap(
			AndworxProject andworxProject, 
			ProjectConfiguration projectConfig, 
			AndroidEnvironment androidEnvironment) {
    	 VariantContextModule variantContextModule = new VariantContextModule(andworxProject, projectConfig, androidEnvironment);
    	 return component.plus(variantContextModule).variantContextMap();
    }
    
	/**
	 * Returns Android configuration read from specified Gradle build file
	 * @param gradleBuildFile Inpt file 
	 * @param androidEnvironment References a single Android SDK installation and resources in the Android environment 
	 * @return AndroidConfigurationBuilder object
	 */
    public AndroidConfigurationBuilder getAndroidConfigBuilder(File gradleBuildFile, AndroidEnvironment androidEnvironment) {
    	ConfigBuilderModule configBuilderModule = new ConfigBuilderModule(gradleBuildFile, androidEnvironment);
    	return component.plus(configBuilderModule).androidConfigurationBuilder();
    }
    
    public RenderscriptCompileTask getRenderscriptCompileTask(VariantContext variantScope, AndroidEnvironment androidEnvironment) {
   	    RenderscriptCompileModule renderscriptCompileModule = new RenderscriptCompileModule(variantScope);
   	    AndroidBuilderModule androidBuilderModule = new AndroidBuilderModule(variantScope, androidEnvironment);
   	    return component.plus(renderscriptCompileModule, androidBuilderModule).renderscriptCompileTask();
    }

    public AidlCompileTask getAidlCompileTask(VariantContext variantScope, AndroidEnvironment androidEnvironment) {
   	    AidlCompileModule aidlCompileModule = new AidlCompileModule(variantScope);
   	    AndroidBuilderModule androidBuilderModule = new AndroidBuilderModule(variantScope, androidEnvironment);
   	    return component.plus(aidlCompileModule, androidBuilderModule).aidlCompileTask();
    }
   
    public AndroidBuilder getAndroidBuilder(VariantContext variantScope, AndroidEnvironment androidEnvironment) {
  	    AndroidBuilderModule androidBuilderModule = new AndroidBuilderModule(variantScope, androidEnvironment);
     	return component.plus(androidBuilderModule).androidBuilder();
    }

    public void startPersistenceService() {
		// Create persistence context before starting Persistence Service
        // Set up dependency injection, which creates an ObjectGraph from an AndworxModule object
        // Note that the table for each entity class will be created in the following step (assuming database is in memory).
        // To populate these tables, call setUp().
        // Get Interface for JPA Support, required to create named queriesPersistenceService.
    	PersistenceContext persistenceContext = getPersistenceContext();
        PersistenceAdmin persistenceAdmin = persistenceContext.getPersistenceAdmin(PersistenceService.PU_NAME);
        logger.info("Andworx database version = " + persistenceAdmin.getDatabaseVersion());
	    PersistenceRunner persistenceRunner = new PersistenceRunner() {

			@Override
			public Executable run(PersistenceWork persistenceWork) {
				return getExecutable(persistenceWork);
			}};
		getPersistenceService().start(persistenceRunner);
	}

    @Override
    public ProjectRegistry getProjectRegistry() {
    	return component.projectRegistry();
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.andworx.BuildFactory#getPersistenceContext()
	 */
    @Override
	public PersistenceContext getPersistenceContext() {
        return component.persistenceContext();
    }
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.BuildFactory#getPersistenceService()
	 */
    @Override
	public PersistenceService getPersistenceService() {
        return component.persistenceService();
    }
 
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.BuildFactory#getAndroidConfiguration()
	 */
    @Override
	public AndroidConfiguration getAndroidConfiguration() {
        return component.androidConfiguration();
    }

	@Override
	public SecurityController getSecurityController() {
		return component.securityController();
	}
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.BuildFactory#getMavenServices()
	 */
    @Override
	public MavenServices getMavenServices() {
    	return component.mavenServices();
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.BuildFactory#getFileManager()
	 */
    @Override
	public FileManager getFileManager() {
    	return component.fileManager();
    }
 
    @Override
    public BuildElementFactory getBuildElementFactory() {
    	return component.buildElementFactory();
    }
 
    @Override
    public BuildHelper getBuildHelper() {
    	return component.buildHelper();
    }
 
    @Override
    public JavaQueuedProcessor getJavaQueuedProcessor() {
    	return component.javaQueuedProcessor();
    }

    @Override
    public TaskFactory getTaskFactory() {
    	return component.taskFactory();
    }
    
    /* (non-Javadoc)
	 * @see org.eclipse.andworx.BuildFactory#getBundleFile(java.lang.String)
	 */
    @Override
	public File getBundleFile(String filePath) {
    	BundleFileModule bundleFileModule =  new BundleFileModule(filePath);
    	return component.plus(bundleFileModule).file();
    }

    /* (non-Javadoc)
	 * @see org.eclipse.andworx.BuildFactory#getExecutable(au.com.cybersearch2.classyjpa.entity.PersistenceWork)
	 */
    @Override
	public Executable getExecutable(PersistenceWork persistenceWork) {
    	PersistenceWorkModule persistenceWorkModule = new PersistenceWorkModule(PersistenceService.PU_NAME, true, persistenceWork);
        return component.plus(persistenceWorkModule).executable();
    }

    @Override
    public PreManifestMergeTask getPreManifestMergeTask(
    		VariantContext variantScope,
			File manifestOutputDir) {
    	PreManifestMergeModule preManifestMergeModule = new PreManifestMergeModule(variantScope, manifestOutputDir);
    	return component.plus(preManifestMergeModule).preManifestMergeTask();
    }
 
    @Override
    public BuildConfigTask getBuildConfigTask(String manifestPackage, VariantContext variantScope) {
    	BuildConfigModule buildConfigModule = new BuildConfigModule(manifestPackage, variantScope);
    	return component.plus(buildConfigModule).buildConfigTask();
    }

    @Override
    public ManifestMergerTask getManifestMergerTask(ManifestMergeHandler manifestMergeHandler) {
    	ManifestMergerModule manifestMergerModule = new ManifestMergerModule(manifestMergeHandler);
    	return component.plus(manifestMergerModule).manifestMergerTask();
    }
    
    @Override
    public MergeResourcesTask getMergeResourcesTask(VariantContext variantScope) {
    	MergeResourcesModule  mergeResourcesModule = new MergeResourcesModule(variantScope);
    	Aapt2ExecutorModule aapt2ExecutorModule = new Aapt2ExecutorModule(variantScope);
    	return component.plus(mergeResourcesModule, aapt2ExecutorModule).mergeResourcesTask();
    }

    @Override
    public NonNamespacedLinkResourcesTask getNonNamespacedLinkResourcesTask(VariantContext variantScope) {
    	LinkResourcesModule  linkResourcesModule = new LinkResourcesModule(variantScope);
    	Aapt2ExecutorModule aapt2ExecutorModule = new Aapt2ExecutorModule(variantScope);
    	return component.plus(linkResourcesModule, aapt2ExecutorModule).linkResourcesTask();
    }

    @Override
    public D8Task getD8Task(Pipeline pipeline, VariantContext variantScope) {
    	D8Module d8Module =  new D8Module(pipeline, variantScope);
  	    return component.plus(d8Module).d8Task();
    }
 
    @Override
    public DesugarTask getDesugarTask(Pipeline pipeline, ProjectBuilder projectBuilder) {
    	DesugarModule desugarModule = new DesugarModule(pipeline, projectBuilder);
    	return component.plus(desugarModule).desugarTask();
    }
    
    @Override
    public PackageApplicationTask getPackageApplicationTask(VariantContext variantScope) {
    	PackageApplicationModule packageApplicationModule = new PackageApplicationModule(variantScope);
      	return component.plus(packageApplicationModule).packageApplicationTask();
    }

    @Override
    public ProjectBuilder getProjectBuilder(IJavaProject javaProject, ProjectProfile profile) {
    	ProjectBuilderModule projectBuilderModule = new ProjectBuilderModule(javaProject, profile);
    	return component.plus(projectBuilderModule).projectBuilder();
    }

    
}
