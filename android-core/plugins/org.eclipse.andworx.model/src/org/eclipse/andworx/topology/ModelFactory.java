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
package org.eclipse.andworx.topology;

import org.eclipse.andworx.jpa.PersistenceService;
import org.eclipse.andworx.modules.ModelProjectReader;
import org.eclipse.andworx.modules.WorkspaceConfiguration;
import org.eclipse.andworx.modules.WorkspaceModeller;
import org.eclipse.andworx.project.AndroidWizardListener;

import au.com.cybersearch2.classyjpa.entity.PersistenceWork;
import au.com.cybersearch2.classyjpa.persist.PersistenceContext;
import au.com.cybersearch2.classytask.Executable;

public interface ModelFactory {

	/**
	 * Returns Model persistence context
	 * @return PersistenceContext object
	 */
	PersistenceContext getPersistenceContext();

	/** 
	 * Returns service to execute persistence tasks 
	 * @return PersistenceService object
	 */
	PersistenceService getPersistenceService();

 	/**
	 * Executes given persistence work and returns object to notify result 
	 * @param persistenceWork
	 * @return Executable object
	 */
	Executable getExecutable(PersistenceWork persistenceWork);

	/**
	 * Starts persistence service. Need call once only.
	 */
	void startPersistenceService();

	/**
	 * Returns Factory for ModelNodeBeans
	 * @return ModelNodeBeanFactory object
	 */
	ModelNodeBeanFactory getModelNodeBeanFactory();

	/**
	 * Returns workspace configuration cotaining information on how projects are associated in the workspace
	 * @return WorkspaceConfiguration object
	 */
	WorkspaceConfiguration getWorkspaceConfiguration();

	/**
	 * Returns workspace modeller
	 * @return WorkspaceModeller object
	 */
	WorkspaceModeller getWorkspaceModeller();

	/**
	 * Returns project import backend
	 * @param androidWizardListener
	 * @return ModelProjectReader object
	 */
	ModelProjectReader getModelProjectReader(AndroidWizardListener androidWizardListener);
}