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
package org.eclipse.andworx.modules;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.eclipse.andworx.AndworxConstants;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.jpa.EntityByPrimaryKeyGenerator;
import org.eclipse.andworx.jpa.EntityBySecondaryKeyGenerator;
import org.eclipse.andworx.jpa.EntityItemByDualKeyGenerator;
import org.eclipse.andworx.jpa.ListQueryTask;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.jpa.PersistenceService.CallableTask;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.project.AndroidConfiguration;
import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.ModelConstants;
import org.eclipse.andworx.topology.entity.ModelNode;
import org.eclipse.andworx.topology.entity.ModelNode.TypedElement;
import org.eclipse.andworx.topology.entity.ModelTypeBean;
import org.eclipse.andworx.topology.entity.ModuleBean;
import org.eclipse.andworx.topology.entity.RepositoryBean;

import com.j256.ormlite.stmt.QueryBuilder;

import au.com.cybersearch2.classyjpa.EntityManagerLite;
import au.com.cybersearch2.classyjpa.entity.EntityManagerDelegate;
import au.com.cybersearch2.classyjpa.entity.PersistenceDao;
import au.com.cybersearch2.classyjpa.entity.PersistenceTask;
import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceAdmin;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;

/**
 * Records and persists how projects are associated in the workspace
 */
public class WorkspaceConfiguration {
	public static final int VOID_MODULE_ID = -1;
	public static final String MODULE_ID_FILE = "module_ID";
    /** Prefix for name of query to fetch entity by dual key */
	private static final String ENTITY_BY_DUAL_KEY  = "org.eclipse.andworx.model.EntityByDualKey";
    /** Prefix for name of query to fetch entity by dual key */
	private static final String ENTITY_BY_SECONDARY_KEY  = "org.eclipse.andworx.model.EntityBySecondaryKey";
    /** Name of query to fetch module by primary key */
	private static final String MODULE_BY_ID = AndroidConfiguration.ENTITY_BY_ID + "0";
	private static final String MODULE_BY_MODEL_TYPE = ENTITY_BY_SECONDARY_KEY + "0";
    /** Name of query to fetch model by name */
	private static final String MODEL_TYPE_BY_DUAL_KEY = ENTITY_BY_DUAL_KEY + "0";
	private static final String MODEL_TYPE_BY_NODE_ID = ENTITY_BY_SECONDARY_KEY + "1";
	private static final String MODULE_BY_NAME = ENTITY_BY_SECONDARY_KEY + "2";
 	private static final String REPOSITORY_BY_MODEL_TYPE = ENTITY_BY_SECONDARY_KEY + "3";
	/** Number of digits in project ID format */
	private static final int ID_LENGTH = 10;

	private static SdkLogger logger = SdkLogger.getLogger(WorkspaceConfiguration.class.getName());

	/** Service to execute persistence tasks */
	private final PersistenceService persistenceService;

	/**
	 * Construct WorkspaceConfiguration object
	 * @param persistenceService Service to execute persistence tasks
	 */
	public WorkspaceConfiguration(PersistenceService persistenceService) {
		this.persistenceService = persistenceService;
		// Start by adding named queries to the persistence context. Use the service to avoid start up synchronization issues.
		try {
			persistenceService.offer(getSetupTask());
		} catch (InterruptedException e) {
			throw new AndworxException("Persistence service startup interrupted", e);
		}
	}

	/**
	 * Find module bean by id located in the "module_ID" file. If this fails, find module by name.
	 * @param moduleLocation
	 * @return ModuleBean object or null if the file is missing or corrupt or module bean not found
	 * @throws InterruptedException
	 */
	public ModuleBean findModule(File moduleLocation) throws IOException, InterruptedException {
		NumberFormat numberFormat = getModuleIdFormat();
        int moduleId = VOID_MODULE_ID;
		File moduleIdFile = new File(moduleLocation, MODULE_ID_FILE);
		ModuleBean moduleBean = null;
		if (moduleIdFile.exists()) {
			List<String> content;
			try {
				content = Files. readAllLines(moduleIdFile.toPath(), StandardCharsets.UTF_8);
				if (content.size() > 0) 
					moduleId = numberFormat.parse(content.get(0)).intValue();
			} catch (IOException | ParseException e) {
				logger.warning("Error reading file %s: %s", moduleIdFile.getAbsolutePath(), e.getMessage());
			}
			if (moduleId == VOID_MODULE_ID) {
				moduleIdFile.delete();
			} else {
				moduleBean = findModuleByIdOrName(moduleId, moduleLocation, moduleIdFile, numberFormat);
			}
		} 
		return moduleBean;
	}

	/**
	 * Returns couut of modules with given project location - expected to be 0 or 1
	 * @param projectLocation Project folder as File object
	 * @return long
	 * @throws InterruptedException
	 */
	public long getLocationCount(File projectLocation) throws InterruptedException {
		long[] result = new long[] {0L};
		String path  = projectLocation.getAbsolutePath();
        PersistenceTask task = new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	// Use ORMLite DAO countOf() method as there is no equivalent in JPA
				EntityManagerDelegate delegate = (EntityManagerDelegate)entityManager.getDelegate();
				@SuppressWarnings("unchecked")
				PersistenceDao<ModuleBean, Integer> dao = 
				    (PersistenceDao<ModuleBean, Integer>) delegate.getDaoForClass(ModuleBean.class);
				QueryBuilder<ModuleBean, Integer> queryBuilder = dao.queryBuilder();
				queryBuilder.setCountOf(true);
                try {
					queryBuilder.where().eq(ModuleBean.MODULE_LOCATION_FIELD_NAME, path);
					result[0] = dao.countOf(queryBuilder.prepare());
				} catch (SQLException e) {
					throw new PersistenceException("Error preparing module count query", e);
				}
            }};
        // Execute work and wait synchronously for completion
        doPersistenceTask("count modules with location " + path, task);
		return result[0];
	}

	/**
	 * Returns name of module for given project folder.
	 * @param moduleLocation Project folder as File object
	 * @return name is guaranteed to be unique
	 * @throws IOException
	 */
	public String getModuleName(File moduleLocation) throws IOException {
		// Convert absolute path to lower case to avoid file system concerns
		return moduleLocation.getCanonicalPath().toLowerCase(Locale.US);
	}

	/**
	 * Returns ModelNode for given project location
	 * @param moduleLocation Project folder as File object
	 * @return ModelNode object containing ModelNodeBean to which node elements are attached
	 * @throws ModelException if no node found to match the location
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public ModelNode getModelNodeByLocation(File moduleLocation) throws IOException, InterruptedException {
		ModelNode[] modelNode = new ModelNode[1];
		// Use module name as search term
		final String moduleName = getModuleName(moduleLocation);
        PersistenceTask task = new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
                Query query = entityManager.createNamedQuery(MODULE_BY_NAME); //
                query.setParameter("name", moduleName);
        	    @SuppressWarnings("unchecked")
        	    // Get result list in case there are unexpected duplicates
				List<ModuleBean> moduleList = (List<ModuleBean>) query.getResultList();
        	    int count = moduleList.size();
        	    if (count == 0) 
        	    	throw new ModelException("Module with name \"" + moduleName + "\" does not exist");
        	    // Choose last module in list to overcome unexpected duplicates
        	    ModuleBean moduleBean = moduleList.get(count - 1);
        	    modelNode[0] = moduleBean.getModelNode();
            }};
        // Execute work and wait synchronously for completion
        doPersistenceTask("get module bean by name " + moduleName, task);
		return modelNode[0];
	}

	/**
	 * Returns ModuleBean for given ModelNode
	 * @param modelNode ModelNode object containing ModelNodeBean to which the ModuleBean is attached
	 * @return ModuleBean object or null if the bean is not found
	 * @throws InterruptedException
	 */
	public ModuleBean getModuleBeanByNode(ModelNode modelNode) throws InterruptedException {
		ModuleBean[] moduleBean = new ModuleBean[1];
        PersistenceTask task = new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	// First find the ModelTypeBean of the required module and then use it to query for the module 
            	// Use ModelType and node id as query terms to find ModelTypeBean
                Query query = entityManager.createNamedQuery(MODEL_TYPE_BY_DUAL_KEY); //
                query.setParameter(1, ModelType.module);
                query.setParameter(2, Integer.valueOf(modelNode.getId()));
                // Query will throw NoResultException if the module is not found
                try {
                	ModelTypeBean modelTypeBean = (ModelTypeBean)query.getSingleResult();
                	query = entityManager.createNamedQuery(MODULE_BY_MODEL_TYPE);
                	query.setParameter(ModuleBean.MODEL_TYPE_ID_FIELD_NAME, Integer.valueOf(modelTypeBean.getId()));
                	moduleBean[0] = (ModuleBean)query.getSingleResult();
                } catch (NoResultException e) {
                	// This is not expected. 
                }
            }};
        // Execute work and wait synchronously for completion
        doPersistenceTask("get module bean by node", task);
		return moduleBean[0];
	}

	/**
	 * Populates given node element list with contents of a tree node identified by ModuleBean
	 * @param moduleBean ModuleBean to identify tree node
	 * @param elementList Element list to populate
	 * @throws InterruptedException
	 */
	public void getElementsByModule(ModuleBean moduleBean, List<TypedElement> elementList) throws InterruptedException {
		// Use ModelNode contained in module bean to find all ModelTypeBeans attached to the node
		ModelNode modelNode = moduleBean.getModelNode();
        PersistenceTask task = new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	Query query = entityManager.createNamedQuery(MODEL_TYPE_BY_NODE_ID);
            	query.setParameter(ModelTypeBean.NODE_ID_FIELD_NAME, Integer.valueOf(Integer.valueOf(modelNode.getId())));
            	@SuppressWarnings("unchecked")
				List<ModelTypeBean> modelTypeBeanList = (List<ModelTypeBean>)query.getResultList();
            	// Add elements according to ModelType
            	for (ModelTypeBean modelTypeBean: modelTypeBeanList) {
            		switch(modelTypeBean.getModelType()) {
            		    case allProjects: elementList.addAll(getRepositories(entityManager, modelTypeBean)); break;
            		    default:
            		};
             	}
            }

			private List<TypedElement> getRepositories(EntityManagerLite entityManager, ModelTypeBean modelTypeBean) {
				List<TypedElement> result = new ArrayList<>();
            	Query query = entityManager.createNamedQuery(REPOSITORY_BY_MODEL_TYPE);
            	query.setParameter(RepositoryBean.MODEL_TYPE_ID_FIELD_NAME, Integer.valueOf(Integer.valueOf(modelTypeBean.getId())));
        		@SuppressWarnings("unchecked")
				List<RepositoryBean> repostioryBeanList = (List<RepositoryBean>)query.getResultList();
        		for (RepositoryBean repositoryBean : repostioryBeanList) {
	        		TypedElement typedElement = new TypedElement();
	        		typedElement.modelType = modelTypeBean.getModelType();
	        		typedElement.nodeElement = repositoryBean;
	        		result.add(typedElement);
        		}
        		return result;
			}};
        // Execute work and wait synchronously for completion
        doPersistenceTask("get elements by module", task);
	}
	
	/**
	 * Find module entity by Id, or if that fails, by name. 
	 * Also updates the module name value if find-by-Id succeeds and the name has changed.
	 * @param moduleId The module entity ID
	 * @param moduleLocation Project location
	 * @param moduleIdFile Path to ID file
	 * @param numberFormat Format used by ID file
	 * @return ModuleBean object 
	 * @throws InterruptedException
	 * @throws IOException 
	 */
	private ModuleBean findModuleByIdOrName(
			int moduleId, 
			File moduleLocation, 
			File moduleIdFile, 
			NumberFormat numberFormat) throws InterruptedException, IOException {
		ModuleBean[] module = new ModuleBean[1];
		final String moduleName = getModuleName(moduleLocation);
        PersistenceTask task = new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
                Query query = entityManager.createNamedQuery(MODULE_BY_ID); //
                query.setParameter("id", moduleId);
                // Query will throw NoResultException if the module is not found
                try {
                	ModuleBean moduleById = (ModuleBean) query.getSingleResult();
            		module[0] = moduleById;
                	if (moduleById.getName().equals(moduleName)) {
                		return;
                	}
                	// TODO - update tree
                	// Rename module but first confirm new name is not assigned to another module
                    query = entityManager.createNamedQuery(MODULE_BY_NAME); //
                    query.setParameter("name", moduleName);
            	    @SuppressWarnings("unchecked")
					List<ModuleBean> moduleList = (List<ModuleBean>) query.getResultList();
            	    if (moduleList.size() > 0) {
            	    	// Generate error to warn user name clash needs to be resolved
            	    	logger.error(null, "Module with ID " + moduleId + " is configured with name \"" + moduleById.getName() + 
            	    			"\" is being renamed to same name as that of module ID " + moduleList.get(0));
            	    }
            	    moduleById.setName(moduleName);
            	    moduleById.setLocation(moduleLocation);
                    entityManager.merge(moduleById);
                } catch (NoResultException e) {
                	// This is not expected. Maybe the module is copied from another workspace.
                	// Delete the module ID file as it no longer useful.
                	moduleIdFile.delete();
                }
            }
        };
        // Execute work and wait synchronously for completion
        doPersistenceTask("find module by id or name", task);
		return module[0] != null ? module[0] : findModuleByName(moduleName);
	}

	/**
	 * Returns module entity bean for given module name
	 * @param moduleName Module name
	 * @return ModuleBean object or null if not found
	 * @throws InterruptedException
	 */
	private ModuleBean findModuleByName(String moduleName) throws InterruptedException {
		ListQueryTask<ModuleBean,String> findModuleTask = new ListQueryTask<>(MODULE_BY_NAME, "name", moduleName);
		doPersistenceTask("find module by name " + moduleName, findModuleTask);
		return findModuleTask.getResultList().isEmpty() ? null : findModuleTask.getResultList().get(0);
	}

	/**
	 * Returns number format for module database ID
	 * @return NumberFormat object
	 */
	NumberFormat getModuleIdFormat() {
		NumberFormat numberFormat = NumberFormat.getIntegerInstance();
		numberFormat.setMaximumIntegerDigits(ID_LENGTH);
		numberFormat.setMinimumIntegerDigits(ID_LENGTH);
		numberFormat.setGroupingUsed(false);		
		return numberFormat;
	}

	/**
	 * Offers task to persistence service. Records transaction sequence number to assist tracing activity. 
	 * Qparam taskTitle
	 * @param persistenceTask Task requiring EntityManager
	 * @throws InterruptedException
	 */
	private void doPersistenceTask(String taskTitle, PersistenceTask persistenceTask) throws InterruptedException {
		if (logger.isLoggable(Level.FINEST))
			logger.verbose("Executing task: %s", taskTitle);
        PersistenceWork task = new PersistenceWork(){
            
            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	persistenceTask.doTask(entityManager);
            	logger.verbose(AndroidConfiguration.DATABASE_COMPLETE_MESSAGE, taskTitle);
            }
            
            @Override
            public void onPostExecute(boolean success)
            {
                if (!success) {
                     logger.error(null, AndroidConfiguration.DATABASE_FAIL_MESSAGE, taskTitle);
                }
            }

            @Override
            public void onRollback(Throwable rollbackException)
            {
                 logger.error(rollbackException, AndroidConfiguration.DATABASE_ROLLBACK_MESSAGE, taskTitle);
            }
        };
        // Execute work and wait synchronously for completion
        executeTask(task);
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
				persistenceService.wait(AndroidConfiguration.DATABASE_TIMEOUT);
		}
	}

	private CallableTask getSetupTask() {
		return new CallableTask() {

			@Override
			public void call(PersistenceContext persistenceContext) throws Exception {
				PersistenceAdmin projectPersistence = persistenceContext.getPersistenceAdmin(ModelConstants.PU_NAME);
		        EntityByPrimaryKeyGenerator entityByPrimaryKeyGenerator = new EntityByPrimaryKeyGenerator();
		        projectPersistence.addNamedQuery(ModuleBean.class, MODULE_BY_ID, entityByPrimaryKeyGenerator);
		        EntityBySecondaryKeyGenerator moduleByNameGenerator = new EntityBySecondaryKeyGenerator("name");
		        projectPersistence.addNamedQuery(ModuleBean.class, MODULE_BY_NAME, moduleByNameGenerator);	
		        EntityItemByDualKeyGenerator moduleTypeGenerator = new EntityItemByDualKeyGenerator("modelType", "node_id");
		        projectPersistence.addNamedQuery(ModelTypeBean.class, MODEL_TYPE_BY_DUAL_KEY, moduleTypeGenerator);
		        EntityBySecondaryKeyGenerator moduleByModelTypeGenerator = new EntityBySecondaryKeyGenerator("model_type_id");
		        projectPersistence.addNamedQuery(ModuleBean.class, MODULE_BY_MODEL_TYPE, moduleByModelTypeGenerator);
		        EntityBySecondaryKeyGenerator modelTypeByNodeIdGenerator = new EntityBySecondaryKeyGenerator("node_id");
		        projectPersistence.addNamedQuery(ModelTypeBean.class, MODEL_TYPE_BY_NODE_ID, modelTypeByNodeIdGenerator);
		        EntityBySecondaryKeyGenerator repoByModelTypeGenerator = new EntityBySecondaryKeyGenerator("model_type_id");
		        projectPersistence.addNamedQuery(RepositoryBean.class, REPOSITORY_BY_MODEL_TYPE, repoByModelTypeGenerator);
		    }

			@Override
			public String getName() {
				return "Set up Workspace Configuration";
			}};
	}
}
