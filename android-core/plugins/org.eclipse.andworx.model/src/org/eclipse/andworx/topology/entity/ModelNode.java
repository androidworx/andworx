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
package org.eclipse.andworx.topology.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.andworx.record.ModelType;
import org.eclipse.andworx.topology.ModelNodeBeanFactory;
import org.eclipse.andworx.topology.ModelPlugin;
import org.eclipse.andworx.topology.NodeElement;

/**
 * Tree node which wraps ModelNodeBean to hide implementation details and ensure tree integrity
 * @param <T> Node element is the node data content
 */
public class ModelNode {

	public static class TypedElement {
		public ModelType modelType;
		public NodeElement nodeElement;
	}
	
	private static ModelNode rootNode;
	private static ModelNodeBeanFactory modelNodeBeanFactory;
	
	static {
		modelNodeBeanFactory = ModelPlugin.getModelFactory().getModelNodeBeanFactory();
		rootNode = new ModelNode();
		rootNode.parent = rootNode;
		ModelNodeBean rootBean = modelNodeBeanFactory.getRootBean();
		rootNode.modelNodeBean = rootBean;
		rootNode.name = rootBean.name;
		rootNode.title = rootBean.title;
	}
	
	private ModelNode parent;
	private ModelNodeBean modelNodeBean;
    private String name;
    private String title;
    private List<TypedElement> nodeElementList;
 	
	/**
	 * Construct a ModelNode root object
	 * @param name
	 * @param title
	 */
	public ModelNode(String name, String title) {
		this(name, title, null);
	}

	public int getId() {
		if (modelNodeBean == null)  
			return 0;
		return modelNodeBean.get_id();
	}
	
	/**
	 * Construct a ModelNode as a child of given parent
	 * @param name
	 * @param title
	 * @param parent
	 */
	public ModelNode(String name, String title, ModelNode parent) {
		this.name = name;
		this.title = title;
		this.parent = parent != null ? parent : rootNode;
	}

	public void attach(List<ModelType>  modelTypes) throws InterruptedException {
		if (modelNodeBean == null) {
			modelNodeBean = modelNodeBeanFactory.instance(name, title, parent.getModelNodeBean());
			modelNodeBeanFactory.persist(modelNodeBean, modelTypes);
			if (nodeElementList != null) {
				for (TypedElement typedElement : nodeElementList)
					modelNodeBeanFactory.attachElement(typedElement.modelType, modelNodeBean, typedElement.nodeElement);
				nodeElementList = null;
			}
		}
	}

	public void attach(ModelType modelType, NodeElement nodeElement) throws InterruptedException {
		if (modelNodeBean == null) {
			if (nodeElementList == null) {
				nodeElementList = new ArrayList<>();
			}
			TypedElement typedElement = new TypedElement();
			typedElement.modelType = modelType;
			typedElement.nodeElement = nodeElement;
			nodeElementList.add(typedElement);
		} else {
			modelNodeBeanFactory.attachElement(modelType, modelNodeBean, nodeElement);
		}
	}
	
	public ModelNode getParent() {
		return parent;
	}

	public List<ModelNode> getChildren() {
		List<ModelNode> childList = new ArrayList<>();
    	if (modelNodeBean != null) {
    		Iterator<ModelNodeBean> iterator = modelNodeBean.get_children().iterator(); 
    		while (iterator.hasNext()) {
    			childList.add(new ModelNode(iterator.next()));
    		}
    	} 
    	return childList;
	}
	
	public String getName() {
		return name;
	}

	public String getTitle() {
		return title;
	}

    public List<ModelType> getModelTypes() {
    	if (modelNodeBean != null) {
    		return modelNodeBean.getModelTypes(); 
    	} else {
    		return Collections.emptyList();
    	}
    }
    
	ModelNode(ModelNodeBean modelNodeBean) {
		this.modelNodeBean = modelNodeBean;
		name = modelNodeBean.name;
		title = modelNodeBean.title;
		if (isRoot(modelNodeBean))
			parent = this;
		else
		    parent = new ModelNode(modelNodeBean.getParent());
	}

	private ModelNode() {
	}
	
	private boolean isRoot(ModelNodeBean modelNodeBean) {
		Collection<ModelTypeBean> modelTypeBeans = modelNodeBean.modelTypeBeans;
		if ((modelTypeBeans == null) || modelTypeBeans.isEmpty())
			return ModelNodeBeanFactory.ROOT_NAME.equals(modelNodeBean.getName());
		Iterator<ModelTypeBean> iterator = modelTypeBeans.iterator();
		return iterator.next().getModelType() == ModelType.root;
	}

	private ModelNodeBean getModelNodeBean() {
		return modelNodeBean;
	}
}
