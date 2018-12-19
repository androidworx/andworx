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
package org.eclipse.andworx.tree;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.Query;

import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.jpa.EntityBySecondaryKeyGenerator;
import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.ModelConstants;
import org.eclipse.andworx.topology.ModelNodeBeanFactory;
import org.eclipse.andworx.topology.NodeElement;
import org.eclipse.andworx.topology.entity.ModelNodeBean;
import org.eclipse.andworx.topology.entity.ModelTypeBean;

import au.com.cybersearch2.classyjpa.EntityManagerLite;
import au.com.cybersearch2.classyjpa.entity.PersistenceTask;
import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceAdmin;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;

/**
 * Implementation of ModelNodeBeanFactory which hides node tree implementation to ensure integrity of the structure.
 */
public class TreeFactory implements ModelNodeBeanFactory {
	/** Name of query to find root node of tree by it's ModelType */
	private static final String FIND_ROOT_MODEL_TYPE = "org.eclipse.andworx.project.FindRoot.Model";

	/** Service to execute persistence tasks */
	private final PersistenceService persistenceService;
	/** Model persistence context */
	private final PersistenceContext persistenceContext;
	/** Root node entity bean - must be created at time of persistence service start */
	private ModelNodeBean rootModelNodeBean;
	/** Named query generator to find ModelTypeBean of root node */
	private EntityBySecondaryKeyGenerator findRootModelTypeBeanGenerator;

	/**
	 * Construct TreeFactory object
	 * @param persistenceService Service to execute persistence tasks
	 * @param persistenceContext Model persistence context
	 */
	public TreeFactory(PersistenceService persistenceService, PersistenceContext persistenceContext) {
		this.persistenceService = persistenceService;
		this.persistenceContext = persistenceContext;
		// Find unique ModelTypeBean with ModelType = "root"
    	findRootModelTypeBeanGenerator = new EntityBySecondaryKeyGenerator("modelType");
    	// Set initial task to create root node
    	persistenceService.addInitialTask(getInitialTask());
	}

	/**
	 * Returns the root node
	 * @return ModelNodeBean object
	 */
	@Override
	public ModelNodeBean getRootBean() {
		return rootModelNodeBean;
	}

	/**
	 * Returns a new ModelNodeBean instance
	 * @param name Name - in format suitable for collating nodes
	 * @param title Title - free text
	 * @param parent Parent ModelNodeBean
	 * @return ModelNodeBean object
	 */
	@Override
	public ModelNodeBean instance(String name, String title, ModelNodeBean parent) {
		if (parent == null)
			parent = rootModelNodeBean;
		ModelNodeBean modelNodeBean = new ModelNodeBean(name, title, parent);
		return modelNodeBean;
	}

	/**
	 * Persist ModelNodeBean object
	 * @param bean ModelNodeBean object
	 * @param modelTypes List of ModelTypes to be attached to the bean
	 * @throws InterruptedException
	 */
	@Override
	public void persist(ModelNodeBean bean, List<ModelType> modelTypes) throws InterruptedException {
		Object monitor = new Object();
		PersistenceWork persistenceWork = getPersistenceWork( new PersistenceTask() {

			@Override
			public void doTask(EntityManagerLite entityManager) {
				// Add bean to it's parent's children to persist it
				try {
					ModelNodeBean parent = bean.getParent();
					if (parent.get_children() == null)
						// ORMLite foreign collection is created by a find operation
						parent = entityManager.find(ModelNodeBean.class, parent.get_id());
					// Adding the bean to a foreign collection causes it to be persisted as a side effect
			        parent.get_children().add(bean);
			        // Persiste model types
					for (ModelType modelType: modelTypes) {
						ModelTypeBean modelTypeBean = new ModelTypeBean(modelType, bean);
						entityManager.persist(modelTypeBean);
					}
				} finally {
					synchronized(monitor) {
						monitor.notifyAll();
					}
				}
			}
		});
		persistenceService.offerTask(persistenceWork);
		synchronized(monitor) {
			monitor.wait();
		}
	}

	/**
	 * Attach node element
	 * @param modelType ModelType of node element
	 * @param nodeBean Tree node to which element is to be attached
	 * @param nodeElement The element
	 * @throws InterruptedException
	 */
	@Override
	public void attachElement(ModelType modelType, ModelNodeBean nodeBean, NodeElement nodeElement) throws InterruptedException {
		Object monitor = new Object();
		PersistenceWork persistenceWork = getPersistenceWork( new PersistenceTask() {

			@Override
			public void doTask(EntityManagerLite entityManager) {
				try {
					ModelTypeBean modelTypeBean = null;
					ModelNodeBean taskNodeBean = nodeBean;
					List<ModelType> modelTypes = nodeBean.getModelTypes();
					if (modelTypes == null) {
						// ORMLite foreign collection is created by a find operation
						taskNodeBean = entityManager.find(ModelNodeBean.class, nodeBean.get_id());
						modelTypes = taskNodeBean.getModelTypes();
					}
					if (!modelTypes.contains(modelType)) {
						modelTypeBean = new ModelTypeBean(modelType,taskNodeBean);
						// Adding the bean to a foreign collection causes it to be persisted as a side effect
						taskNodeBean.getModelTypeBeans().add(modelTypeBean);
					} else {
						modelTypeBean = taskNodeBean.getModelTypeBean(modelType);
					}
					nodeElement.setModelTypeBean(modelTypeBean);
					entityManager.persist(nodeElement);
				} finally {
					synchronized(monitor) {
						monitor.notifyAll();
					}
				}
			}
		});
		persistenceService.offerTask(persistenceWork);
		synchronized(monitor) {
			monitor.wait();
		}
	}

	/**
	 * Returns root node creation task to be performed by persistence service before it starts
	 * @return
	 */
	private PersistenceWork getInitialTask() {
		return getPersistenceWork( new PersistenceTask() {

			@Override
			public void doTask(EntityManagerLite entityManager) {
				// Add named query to find root node ModelTypeBean and then run it
				PersistenceAdmin projectPersistence = persistenceContext.getPersistenceAdmin(ModelConstants.PU_NAME);
		        projectPersistence.addNamedQuery(ModelTypeBean.class, FIND_ROOT_MODEL_TYPE, findRootModelTypeBeanGenerator);
		        Query query = entityManager.createNamedQuery(FIND_ROOT_MODEL_TYPE);
		        ModelTypeBean modelTypeBean = null;
		        query.setParameter("modelType", ModelType.root);
		        try {
		        	modelTypeBean = (ModelTypeBean) query.getSingleResult();
		        } catch (NoResultException e) {
		        	
		        }
		        if (modelTypeBean == null) {
		        	// Root node needs to be created
		        	rootModelNodeBean = new ModelNodeBean(ModelNodeBeanFactory.ROOT_NAME, ModelNodeBeanFactory.ROOT_NAME, null);
		        	rootModelNodeBean.setParent(rootModelNodeBean); 
	            	entityManager.persist(rootModelNodeBean);
	            	modelTypeBean = new ModelTypeBean(ModelType.root, rootModelNodeBean);
	            	if (rootModelNodeBean.getModelTypeBeans() == null)
	            		rootModelNodeBean = entityManager.find(ModelNodeBean.class, rootModelNodeBean.get_id());
	            	rootModelNodeBean.getModelTypeBeans().add(modelTypeBean);
		        } else {
		        	rootModelNodeBean = modelTypeBean.getNode();
		        }
			}

        });

	}
	
	/**
	 * Offers task to persistence service. Records transaction sequence number to assist tracing activity. 
	 * @param persistenceTask Task requiring EntityManager
	 */
	private PersistenceWork getPersistenceWork(PersistenceTask persistenceTask) {
		return new PersistenceWork() {

			@Override
			public void doTask(EntityManagerLite entityManager) {
				persistenceTask.doTask(entityManager);
			}

			@Override
			public void onPostExecute(boolean success) {
				if (!success)
					throw new AndworxException("Model persistence service failed at start");
			}

			@Override
			public void onRollback(Throwable rollbackException) {
				throw new AndworxException("Model persistence service failed at start", rollbackException);
			}};
	}
}
