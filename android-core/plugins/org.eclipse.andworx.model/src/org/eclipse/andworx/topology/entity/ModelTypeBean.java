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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import org.eclipse.andworx.record.ModelType;

/**
 * Associates a Node with one or more model classes
 */
@Entity(name = "tableModelTypes")
public class ModelTypeBean {

    /** Column name in join table for model type foreign key */
	public final static String MODEL_TYPE_ID_FIELD_NAME = "id";
	public final static String NODE_ID_FIELD_NAME = "node_id";

	@Id @GeneratedValue
    int id;

    @OneToOne
    @JoinColumn(name=NODE_ID_FIELD_NAME, referencedColumnName="_id")
	ModelNodeBean node;
	
    @Column(nullable = false)
    ModelType modelType;

    ModelTypeBean() {
	}
    
    public ModelTypeBean(ModelType modelType, ModelNodeBean node) {
    	this.modelType = modelType;
    	this.node = node;
	}

    public int getId() {
    	return id;
    }
    
	public ModelType getModelType() {
		return modelType;
	}

	public ModelNodeBean getNode() {
		return node;
	}

}
