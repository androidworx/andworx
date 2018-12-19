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

import java.util.List;

import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.entity.ModelNodeBean;

/**
 * Factory for ModelNodeBeans which ensures there are no orphan nodes and also 
 * takes care of creating the root node
 */
public interface ModelNodeBeanFactory {

	public static String ROOT_NAME = "_ROOT";

	/**
	 * Returns a new ModelNodeBean instance
	 * @param name Name - in format suitable for collating nodes
	 * @param title Title - free text
	 * @param parent Parent ModelNodeBean
	 * @return ModelNodeBean object
	 */
	ModelNodeBean instance(String name, String title, ModelNodeBean parent);
	
	/**
	 * Persist ModelNodeBean object
	 * @param bean ModelNodeBean object
	 * @param modelTypes List of ModelTypes to be attached to the bean
	 * @throws InterruptedException
	 */
	void persist(ModelNodeBean bean, List<ModelType>  modelTypes) throws InterruptedException;

	/**
	 * Returns the root node
	 * @return ModelNodeBean object
	 */
	ModelNodeBean getRootBean();

	/**
	 * Attach node element
	 * @param modelType ModelType of node element
	 * @param nodeBean Tree node to which element is to be attached
	 * @param nodeElement The element
	 * @throws InterruptedException
	 */
	void attachElement(ModelType modelType, ModelNodeBean nodeBean, NodeElement nodeElement) throws InterruptedException;
}
