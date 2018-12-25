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
package org.eclipse.andworx.project;

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.NoResultException;
import javax.persistence.Query;

import org.eclipse.e4.core.services.events.IEventBroker;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.eclipse.andworx.event.AndworxEvents;

import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.build.DefaultAndroidSourceSet;
import org.eclipse.andworx.config.ConfigContext;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.entity.AndroidBean;
import org.eclipse.andworx.entity.AndroidSourceBean;
import org.eclipse.andworx.entity.BaseConfigBean;
import org.eclipse.andworx.entity.BuildTypeBean;
import org.eclipse.andworx.entity.DependencyBean;
import org.eclipse.andworx.entity.ProductFlavorBean;
import org.eclipse.andworx.entity.ProjectBean;
import org.eclipse.andworx.entity.ProjectProfileBean;
import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.file.AndroidSourceSet;
import org.eclipse.andworx.jpa.EntityByPrimaryKeyGenerator;
import org.eclipse.andworx.jpa.EntityByProjectIdGenerator;
import org.eclipse.andworx.jpa.EntityBySecondaryKeyGenerator;
import org.eclipse.andworx.jpa.EntityOperation;
import org.eclipse.andworx.jpa.ListQueryTask;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.jpa.PersistenceService.CallableTask;
import org.eclipse.andworx.jpa.SingleQueryTask;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.model.BuildTypeImpl;
import org.eclipse.andworx.model.ProductFlavorImpl;
import org.eclipse.andworx.model.ProjectSourceProvider;
import org.eclipse.andworx.model.SourceSet;
import org.eclipse.andworx.repo.DependencyArtifact;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.android.builder.core.BuilderConstants;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;

import au.com.cybersearch2.classyjpa.EntityManagerLite;
import au.com.cybersearch2.classyjpa.entity.PersistenceTask;
import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceAdmin;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;

/** 
 * Android configuration persisted using JPA
 */
public class AndroidConfiguration {
	public static final int VOID_PROJECT_ID = -1;
	public static final String PROJECT_ID_FILE = "project_ID";
    /** Number of digits in project ID format */
	private static final int ID_LENGTH = 10;
	/** Duration in milliseconds to wait for datgbase  operation to complete - 20 seconds */
	public static final long DATABASE_TIMEOUT = 20000;
    /** Prefix for name of query to fetch entity by primary key */
	public static final String ENTITY_BY_ID = "org.eclipse.andworx.project.EntityById";
    /** Prefix for name of query to fetch entity by project ID */
	private static final String ENTITY_BY_PROJECT_ID = "org.eclipse.andworx.project.EntityByProjectId";
    /** Prefix for name of query to fetch entity by project ID */
	//private static final String ITEM_BY_DUAL_KEY = "org.eclipse.andworx.project.ItemByDualKey";
    /** Name of query to fetch project by primary key */
	private static final String PROJECT_BY_ID = ENTITY_BY_ID + "0";
	/** Name of query to fetch Signing Config by ID */
	private static final String SIGNING_CONFIG_BY_ID = ENTITY_BY_ID + "1";
    /** Name of query to fetch project by name */
	private static final String PROJECT_BY_NAME = "org.eclipse.andworx.project.ProjectByName";
    /** Name of query to fetch project profile by project ID */
	private static final String PROJECT_PROFILE_BY_ID = ENTITY_BY_PROJECT_ID + "0";
    /** Name of query to fetch dependency by project ID */
	private static final String DEPENDENCY_BY_ID = ENTITY_BY_PROJECT_ID + "1";
    /** Name of query to fetch default config by project ID */
	private static final String DEFAULT_CONFIG_BY_ID = ENTITY_BY_PROJECT_ID + "2";
	/** Name of query to fetch project flavor by project ID */
	private static final String PRODUCT_FLAVOR_BY_ID = ENTITY_BY_PROJECT_ID + "3";
	/** Name of query to fetch build type by project ID */
	private static final String BUILDTYPE_BY_ID = ENTITY_BY_PROJECT_ID + "4";
	/** Name of query to fetch base config by project ID */
	private static final String BASE_CONFIG_BY_ID = ENTITY_BY_PROJECT_ID + "5";
	/** Name of query to fetch Android source by project ID */
	private static final String ANDROID_SOURCE_BY_ID = ENTITY_BY_PROJECT_ID + "6";
	
	public static final String DATABASE_COMPLETE_MESSAGE = "Database operation %s completed";
	public static final String DATABASE_ROLLBACK_MESSAGE = "Database operation %s failed. See logs for details.";
	public static final String DATABASE_FAIL_MESSAGE = "Database operation %s was teriminated";

	private static SdkLogger logger = SdkLogger.getLogger(AndroidConfiguration.class.getName());

	/** Service to execute persistence tasks sequentially */
	private final PersistenceService persistenceService;
	/** Persistent task utility to Facilitate simple operations such as persist and delete */
	private final EntityOperation entityOp;
	protected EventHandler updateEventHandler = new EventHandler() {

		@Override
		public void handleEvent(Event event) {
			Object eventData = event.getProperty(IEventBroker.DATA);
			if ((eventData != null) && (eventData instanceof SigningConfigBean))
				handleUpdate((SigningConfigBean)eventData);
		}};

	/**
	 * Construct AndroidConfiguration object
	 * @param persistenceService Service to execute persistence tasks sequentially
	 */
	public AndroidConfiguration(PersistenceService persistenceService, IEventBroker eventBroker) {
		this.persistenceService = persistenceService;
		entityOp = new EntityOperation();
		// Start by adding named queries to the persistence context. Use the service to avoid start up synchronization issues.
		try {
			persistenceService.offer(getSetupTask(eventBroker));
		} catch (InterruptedException e) {
			throw new AndworxException("Persistence service startup interrupted", e);
		}
	}

	/**
	 * Find project by id located in the "project_ID" file. If this fails, find project by name.
	 * @param projectName
	 * @param projectLocation
	 * @return ProjectBean object or null if the file is missing or corrupt or project bean not found
	 * @throws InterruptedException
	 */
	public ProjectBean findProject(String projectName, File projectLocation) throws InterruptedException {
		NumberFormat numberFormat = getProjectIdFormat();
        int projectId = VOID_PROJECT_ID;
		File projectIdFile = new File(projectLocation, PROJECT_ID_FILE);
		ProjectBean projectBean = null;
		if (projectIdFile.exists()) {
			List<String> content;
			try {
				content = Files. readAllLines(projectIdFile.toPath(), StandardCharsets.UTF_8);
				if (content.size() > 0) 
					projectId = numberFormat.parse(content.get(0)).intValue();
			} catch (IOException | ParseException e) {
				logger.warning("Error reading file %s: %s", projectIdFile.getAbsolutePath(), e.getMessage());
			}
			if (projectId == VOID_PROJECT_ID) {
				projectIdFile.delete();
			} else {
				projectBean = findProjectByIdOrName(projectId, projectName, projectIdFile, numberFormat);
			}
		} 
		return projectBean;
	}

	/**
	 * Create table entries for a new project
	 * @param projectName Name of Eclipse project
	 * @param projectProfile
	 * @return ProjectBean object which has been persisted an therefore contains a generated ID
	 * @throws InterruptedException
	 */
	public ProjectBean createProject(String projectName, ProjectProfile projectProfile) throws InterruptedException {
		ProjectBean projectBean = findProjectByName(projectName); //, projectIdFile, numberFormat);
		if (projectBean != null) { // This is not expected but possibly a project name is being recycled
			doPersistenceTask("delete project bean", entityOp.delete(projectBean));
		}
		// The project bean has an ID and Eclipse project name.
    	ProjectBean newProject = new ProjectBean();
    	newProject.setName(projectName);
		PersistenceTask persistenceTask = new PersistenceTask() {

			@Override
			public void doTask(EntityManagerLite entityManager) {
				entityManager.persist(newProject);
				// Persist the project profile excluding dependencies
				projectProfile.persist(entityManager, newProject);
				// Now persist the dependencies
				for (Dependency dependency: projectProfile.getDependencies()) 
					persistDependency(entityManager, dependency);
			}

			private void persistDependency(EntityManagerLite entityManager, Dependency dependency) {
				// Dependencies are associated with projects by project ID
				DependencyBean dependencyBean = 
					new DependencyBean(
						newProject, 
						dependency.getIdentity());
				dependencyBean.setLibrary(dependency.isLibrary());
				// File system paths are serialized as Strings
				String path = dependency.getPath() != null ? dependency.getPath().getAbsolutePath() : "";
				dependencyBean.setPath(path);
				entityManager.persist(dependencyBean);
		}};
		doPersistenceTask("create project " + projectName, persistenceTask);
		return newProject;
	}

	/**
	 * Creates project file containing project Id
	 * @param projectId Project ID
	 * @param projectLocation Project location on file system
	 * @throws IOException
	 */
	public void createProjectIdFile(int projectId, File projectLocation) throws IOException {
		NumberFormat numberFormat = getProjectIdFormat();
 		File projectIdFile = new File(projectLocation, PROJECT_ID_FILE);
		if (projectIdFile.exists())
			projectIdFile.delete();
		else if (!projectIdFile.getParentFile().exists() && !projectIdFile.getParentFile().mkdirs())
			throw new AndworxException("Error creating project directory " + projectIdFile.getParentFile().toString());
        String formatedId = numberFormat.format(projectId);
        projectIdFile.setReadOnly();
		Files.write(projectIdFile.toPath(), formatedId.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
	}

	/**
	 * Persists Android configuration and returns project profile updated with new databse project ID
	 * @param projectBean Project entity bean
	 * @param androidConfigurationBuilder Object which assembles parsed nodes of build.gradle configuration
	 * @throws InterruptedException
	 */
	public void persist(ProjectBean projectBean, AndroidDigest androidDigest) throws InterruptedException {
		PersistenceTask persistenceTask = new PersistenceTask() {

			@Override
			public void doTask(EntityManagerLite entityManager) {
				Object[] entities = androidDigest.asEntities(projectBean);
				for (Object bean: entities)
					entityManager.persist(bean);
			}};
		doPersistenceTask("persist project bean for " + projectBean.getName(), persistenceTask);
	}

	/**
	 * Returns ProjectProfile object marshalled from persistence context
	 * @param projectName Eclipse project name
	 * @param projectLocation Project location on file system
	 * @return ProjectProfile object or null if project or project profile cannot be found
	 * @throws InterruptedException
	 */
	public ProjectProfile getProfile(String projectName, File projectLocation) throws InterruptedException {
		// Find project entity by id extracted from project ID file. 
		// NOTE. This will fail for first time after a project has been imported, and the fallback will be required.
		ProjectBean projectBean = findProject(projectName, projectLocation);
		boolean projectFoundById = projectBean != null;
		if (!projectFoundById) {
			// Find project by name as a fallback.
			projectBean = findProjectByName(projectName);
		}
		// Use an array for inner class access
		final ProjectProfile[] projectProfile = new ProjectProfile[] {null};
		if (projectBean != null) {
			int projectId = projectBean.getId();
			PersistenceTask persistenceTask = new PersistenceTask() {

				@Override
				public void doTask(EntityManagerLite entityManager) {
					// Use named query PROJECT_PROFILE_BY_ID with method getSingleResult().
	                Query query = entityManager.createNamedQuery(PROJECT_PROFILE_BY_ID); //
	                query.setParameter(ProjectProfileBean.PROJECT_ID_FIELD_NAME, projectId);
	                // Query will throw NoResultException if the project is not found
	                try {
	                	ProjectProfileBean projectProfileBean = (ProjectProfileBean) query.getSingleResult();
	                	projectProfile[0] = new ProjectProfile(projectProfileBean);
	                	// Populate ProjectProfile object with dependencies
	                	query = entityManager.createNamedQuery(DEPENDENCY_BY_ID); //
		                query.setParameter(DependencyBean.PROJECT_ID_FIELD_NAME, projectId);
                        @SuppressWarnings("unchecked")
						List<DependencyBean> dependencyBeans = (List<DependencyBean>)query.getResultList();
                        for (DependencyBean dependencyBean: dependencyBeans) {
                        	DependencyArtifact dependency = 
                        		new DependencyArtifact(
                        			dependencyBean.getGroupId(),
                        			dependencyBean.getArtifactId(),
                        			dependencyBean.getVersion());
                        	dependency.setLibrary(dependencyBean.isLibrary());
                        	File path = dependencyBean.getPath();
                        	if (!path.getPath().isEmpty())
                        		dependency.setPath(path);
                        	projectProfile[0].addDependency(dependency);
                        }
	                	
	                } catch (NoResultException e) {
	                	logger.warning("Project profile not found for project %s", projectName);
	                }
				}};
			doPersistenceTask("get profile for project " + projectName, persistenceTask);
		}
		return projectProfile[0];
	}

	/**
	 * Returns project configuration
	 * @param profile Project profile
	 * @param projectName Project name  
	 * @param projectLocation Project absolute location
	 * @param androidEnvironment References a single Android SDK installation and resources in the Android environment
	 * @return ProjectConfiguration object
	 * @throws InterruptedExceptionSigningConfigBean
	 */
	public ProjectConfiguration getProjectConfiguration(
			ProjectProfile profile,
			String projectName,
			File projectLocation,
			AndroidEnvironment androidEnvironment) throws InterruptedException {
		int projectId = profile.getProjectId();
		SingleQueryTask<AndroidBean, Integer> findDefaultConfigTask = 
				new SingleQueryTask<>(DEFAULT_CONFIG_BY_ID, AndroidBean.PROJECT_ID_FIELD_NAME, projectId);
		ListQueryTask<ProductFlavorBean,Integer> findProductFlavorsTask = 
				new ListQueryTask<>(PRODUCT_FLAVOR_BY_ID, ProductFlavorBean.PROJECT_ID_FIELD_NAME, projectId);
		ListQueryTask<BuildTypeBean,Integer> findBuildTypesTask = 
				new ListQueryTask<>(BUILDTYPE_BY_ID, BuildTypeBean.PROJECT_ID_FIELD_NAME, projectId);
		ListQueryTask<BaseConfigBean,Integer> findBaseConfigsTask = 
				new ListQueryTask<>(BASE_CONFIG_BY_ID, BaseConfigBean.PROJECT_ID_FIELD_NAME, projectId);
		ListQueryTask<AndroidSourceBean,Integer> findAndroidSourcesTask = 
				new ListQueryTask<>(ANDROID_SOURCE_BY_ID, AndroidSourceBean.PROJECT_ID_FIELD_NAME, projectId);
		doPersistenceTask("find default config for " + projectName, findDefaultConfigTask);
		doPersistenceTask("find product flavors for " + projectName, findProductFlavorsTask);
		doPersistenceTask("find buildtypes for " + projectName, findBuildTypesTask);
		doPersistenceTask("find base configs for " + projectName, findBaseConfigsTask);
		doPersistenceTask("find Android sources for " + projectName, findAndroidSourcesTask);
		AndroidBean androidBean = findDefaultConfigTask.getResult();  
		Collection<ProductFlavor> productFlavors = new ArrayList<>();
		Collection<BuildType> buildTypes = new ArrayList<>();
		Collection<ProjectSourceProvider> sourceProviders = new ArrayList<>();
		Map<String,BaseConfigBean> baseConfigMap = new HashMap<>();
		for (BaseConfigBean bean: findBaseConfigsTask.getResultList())
			baseConfigMap.put(bean.getName(), bean);
		for (ProductFlavorBean bean: findProductFlavorsTask.getResultList()) {
			BaseConfigBean baseConfigBean = baseConfigMap.get(bean.getName());
			if (baseConfigBean == null)
				throw new AndworxException("Base config named \"" + bean.getName() + "\" not found");
			ProductFlavorImpl productFlavor = new ProductFlavorImpl(bean, baseConfigBean);
			if (bean.getName().equals(BuilderConstants.MAIN)) {
				androidBean.setDefaultConfig(productFlavor);
			}
			productFlavors.add(productFlavor);
		}
		for (BuildTypeBean bean: findBuildTypesTask.getResultList()) {
			BaseConfigBean baseConfigBean = baseConfigMap.get(bean.getName());
			if (baseConfigBean == null)
				throw new AndworxException("Base config named \"" + bean.getName() + "\" not found");
			setSigningConfig(bean);
			BuildTypeImpl buildType = new BuildTypeImpl(bean, baseConfigBean);
			buildTypes.add(buildType);
		}
		SourceProvider mainSourceProvider = null;
		for (AndroidSourceBean bean: findAndroidSourcesTask.getResultList()) {
			AndroidSourceSet androidSourceSet = new AndroidSourceSet(bean, projectLocation);
			DefaultAndroidSourceSet sourceSet = new DefaultAndroidSourceSet(androidSourceSet);
			if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()))
				mainSourceProvider = sourceSet;	
			sourceProviders.add(sourceSet);
		}
		if (mainSourceProvider == null) {
			throw new AndworxException("Default source set named \"" + SourceSet.MAIN_SOURCE_SET_NAME + "\" not found");
		}
		Collection<BuildTypeContainer> buildTypeContainers = new ArrayList<>();
		boolean hasDebugBuildType = false;
		boolean hasReleaseBuildType = false;
		for (BuildType buildType: buildTypes) {
			buildTypeContainers.add(createContainer(buildType, mainSourceProvider));
			if (DEBUG.equals(buildType.getName()))
				hasDebugBuildType = true;
			if (RELEASE.equals(buildType.getName()))
				hasReleaseBuildType = true;
		}
		if (!hasDebugBuildType) {
			BuildTypeBean buildTypeAtom = new BuildTypeBean(DEBUG);
			buildTypeAtom.setDebuggable(true);
			buildTypeAtom.setSigningConfig(androidEnvironment.getDefaultDebugSigningConfig());
			BuildTypeImpl buildType = new BuildTypeImpl(buildTypeAtom, new BaseConfigBean(DEBUG));
			buildTypeContainers.add(createContainer(buildType, mainSourceProvider));
		}
		if (!hasReleaseBuildType) {
			BuildTypeBean buildTypeAtom = new BuildTypeBean(RELEASE);
			buildTypeAtom.setSigningConfig(androidEnvironment.getDefaultDebugSigningConfig());
			BuildTypeImpl buildType = new BuildTypeImpl(buildTypeAtom, new BaseConfigBean(RELEASE));
			buildTypeContainers.add(createContainer(buildType, mainSourceProvider));
		}
		if (androidBean.getDefaultConfig() == null)
			throw new AndworxException("Default config not found");
		return new ProjectConfiguration(
				projectName, 
				profile, 
				projectLocation, 
				androidBean, 
				productFlavors, 
				buildTypeContainers, 
				sourceProviders);
	}

	protected void handleUpdate(SigningConfigBean bean) {
		Job job = new Job("Update " + bean.getName() + " signing Config") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					doPersistenceTask(getName(), entityOp.update(bean));
				} catch (Exception e) {
					logger.error(e, "Error running job \"%\"", getName());
					return Status.CANCEL_STATUS;
				}
				return Status.OK_STATUS;
			}
		};
        job.schedule();
	}

	/**
	 * Set signing config in given entity bean
	 * @param bean BuildType entity bean
	 * @throws InterruptedException
	 */
	private void setSigningConfig(BuildTypeBean bean) throws InterruptedException {
		int id = bean.getSigningConfigId();
		if (id == 0) 
			return;
		SigningConfigBean[] signingConfigBean = new SigningConfigBean[]{null};
        PersistenceTask task = new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
		        Query query = entityManager.createNamedQuery(SIGNING_CONFIG_BY_ID); //
		        query.setParameter("id", id);
		        // Query will throw NoResultException if the project is not found
		        try {
		        	signingConfigBean[0] = (SigningConfigBean) query.getSingleResult();
                } catch (NoResultException e) {
                	
                }
            }};
         doPersistenceTask("set " + bean.getName() + " signing config", task);
         bean.setSigningConfig(signingConfigBean[0]);
	}

	/**
	 * Returns BuildType container for given BuildType and SourceProvider
	 * @param buildType Build type
	 * @param buildTypeSourceProvider Source provider
	 * @return BuildTypeContainer object
	 */
	private BuildTypeContainer createContainer(BuildType buildType, SourceProvider buildTypeSourceProvider) {
		return new BuildTypeContainer() {
			@Override
			public BuildType getBuildType() {
				return buildType;
			}
			@Override
			public SourceProvider getSourceProvider() {
				return buildTypeSourceProvider;
			}
			@Override
			public Collection<SourceProviderContainer> getExtraSourceProviders() {
				return Collections.emptyList();
			}};
	}

	/**
	 * Find project entity by Id, or if that fails, by name. 
	 * Also updates  the project name value if find-by-Id succeeds and the name has changed.
	 * @param projectId The project entity ID
	 * @param projectName Eclipse project name
	 * @param projectIdFile Path to ID file
	 * @param numberFormat Format used by ID file
	 * @return ProjectBean object 
	 * @throws InterruptedException
	 */
	private ProjectBean findProjectByIdOrName(
			int projectId, 
			String projectName, 
			File projectIdFile, 
			NumberFormat numberFormat) throws InterruptedException {
		ProjectBean[] project = new ProjectBean[1];
        PersistenceTask task = new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
                Query query = entityManager.createNamedQuery(PROJECT_BY_ID); //
                query.setParameter("id", projectId);
                // Query will throw NoResultException if the project is not found
                try {
                	ProjectBean projectById = (ProjectBean) query.getSingleResult();
            		project[0] = projectById;
                	if (projectById.getName().equals(projectName)) {
                		return;
                	}
                	// Rename project but first confirm new name is not assigned to another project
                    query = entityManager.createNamedQuery(PROJECT_BY_NAME); //
                    query.setParameter("name", projectName);
            	    @SuppressWarnings("unchecked")
					List<ProjectBean> projectList = (List<ProjectBean>) query.getResultList();
            	    if (projectList.size() > 0) {
            	    	// Generate error to warn user name clash needs to be resolved
            	    	logger.error(null, "Project with ID " + projectId + " is configured with name \"" + projectById.getName() + 
            	    			"\" is being renamed to same name as that of project ID " + projectList.get(0));
            	    }
            	    projectById.setName(projectName);
                    entityManager.merge(projectById);
                } catch (NoResultException e) {
                	// This is not expected. Maybe the project is copied from another workspace.
                	// Delete the project ID file as it no longer useful.
                	projectIdFile.delete();
                }
            }
        };
        // Execute work and wait synchronously for completion
        doPersistenceTask("find project by id or name", task);
		return project[0] != null ? project[0] : findProjectByName(projectName);
	}

	/**
	 * Offers task to persistence service. Records transaction sequence number to assist tracing activity. 
	 * Qparam taskTitle
	 * @param persistenceTask Task requiring EntityManager
	 * @throws InterruptedException
	 */
	private void doPersistenceTask(String taskTitle, PersistenceTask persistenceTask) throws InterruptedException {
        PersistenceWork task = new PersistenceWork(){
            
            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	persistenceTask.doTask(entityManager);
            	logger.verbose(DATABASE_COMPLETE_MESSAGE, taskTitle);
            }
            
            @Override
            public void onPostExecute(boolean success)
            {
                if (!success) {
                     logger.error(null, DATABASE_FAIL_MESSAGE, taskTitle);
                }
            }

            @Override
            public void onRollback(Throwable rollbackException)
            {
                 logger.error(rollbackException, DATABASE_ROLLBACK_MESSAGE, taskTitle);
            }
        };
        // Execute work and wait synchronously for completion
        executeTask(task);
	}

	/**
	 * Returns number format for project database ID
	 * @return NumberFormat object
	 */
	private NumberFormat getProjectIdFormat() {
		NumberFormat numberFormat = NumberFormat.getIntegerInstance();
		numberFormat.setMaximumIntegerDigits(ID_LENGTH);
		numberFormat.setMinimumIntegerDigits(ID_LENGTH);
		numberFormat.setGroupingUsed(false);		
		return numberFormat;
	}


	/**
	 * Returns project entity bean for given project name
	 * @param projectName Project name
	 * @return ProjectBean object or null if not found
	 * @throws InterruptedException
	 */
	private ProjectBean findProjectByName(String projectName) throws InterruptedException {
		ListQueryTask<ProjectBean,String> findProjectTask = new ListQueryTask<>(PROJECT_BY_NAME, "name", projectName);
		doPersistenceTask("find prject by name " + projectName, findProjectTask);
		return findProjectTask.getResultList().isEmpty() ? null : findProjectTask.getResultList().get(0);
	}

	/**
	 * Execute given unit of persistence work
	 * @param task persistence work
	 * @throws InterruptedException
	 */
	private void executeTask(PersistenceWork task) throws InterruptedException {
		persistenceService.offerTask(task);
		synchronized(persistenceService) {
			// Wait indefiinitely when debugging
			boolean isDevelopmentMode = System.getenv(AndworxConstants.DEVELOPMENT_MODE) != null;
			if (isDevelopmentMode)
				persistenceService.wait();
			else
				persistenceService.wait(DATABASE_TIMEOUT);
		}
	}

	private CallableTask getSetupTask(IEventBroker eventBroker) {
		return new CallableTask() {

			@Override
			public void call(PersistenceContext persistenceContext) throws Exception {
				PersistenceAdmin projectPersistence = persistenceContext.getPersistenceAdmin(AndworxConstants.PU_NAME);
		        EntityByPrimaryKeyGenerator entityByPrimaryKeyGenerator = new EntityByPrimaryKeyGenerator();
		        projectPersistence.addNamedQuery(ProjectBean.class, PROJECT_BY_ID, entityByPrimaryKeyGenerator);
		        projectPersistence.addNamedQuery(SigningConfigBean.class, SIGNING_CONFIG_BY_ID, entityByPrimaryKeyGenerator);
		        EntityBySecondaryKeyGenerator entityByNameGenerator = new EntityBySecondaryKeyGenerator("name");
		        projectPersistence.addNamedQuery(ProjectBean.class, PROJECT_BY_NAME, entityByNameGenerator);
		        EntityByProjectIdGenerator entityByProjectIdGenerator = new EntityByProjectIdGenerator();
		        projectPersistence.addNamedQuery(ProjectProfileBean.class, PROJECT_PROFILE_BY_ID, entityByProjectIdGenerator);
		        projectPersistence.addNamedQuery(DependencyBean.class, DEPENDENCY_BY_ID, entityByProjectIdGenerator);
		        projectPersistence.addNamedQuery(AndroidBean.class, DEFAULT_CONFIG_BY_ID, entityByProjectIdGenerator);
		        projectPersistence.addNamedQuery(ProductFlavorBean.class, PRODUCT_FLAVOR_BY_ID, entityByProjectIdGenerator);
		        projectPersistence.addNamedQuery(BuildTypeBean.class, BUILDTYPE_BY_ID, entityByProjectIdGenerator);
		        projectPersistence.addNamedQuery(BaseConfigBean.class, BASE_CONFIG_BY_ID, entityByProjectIdGenerator);
		        projectPersistence.addNamedQuery(AndroidSourceBean.class, ANDROID_SOURCE_BY_ID, entityByProjectIdGenerator);
		        eventBroker.subscribe(AndworxEvents.UPDATE_ENTITY_BEAN, updateEventHandler );
			}

			@Override
			public String getName() {
				return "Set up Android Configuration";
			}};
	}
}
