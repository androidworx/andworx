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

import java.io.File;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import org.eclipse.andworx.topology.NodeElement;

/**
 * Data pertaining to one module
 */
@Entity(name = "tableModules")
public class ModuleBean implements NodeElement {

    /** Column name in join table for module foreign key */
	public final static String MODULE_ID_FIELD_NAME = "id";
    /** Column name in join table for module foreign key */
	public final static String MODULE_LOCATION_FIELD_NAME = "location";
	public final static String MODEL_TYPE_ID_FIELD_NAME = "model_type_id";

	@Id @GeneratedValue
    int id;

    @Column(nullable = false)
    String name;
    
    @Column(nullable = false)
    String location;
    
    @OneToOne
    @JoinColumn(name=MODEL_TYPE_ID_FIELD_NAME, referencedColumnName="id")
    ModelTypeBean modelTypeBean;

    ModuleBean() {
    }

    public ModuleBean(String name, File location) {
    	this.name = name;
    	this.location = location.getAbsolutePath();
    }
    
	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public File getLocation() {
		return new File(location);
	}
	
	public String getLocationValue() {
		return location;
	}

	public ModelTypeBean getModelTypeBean() {
		return modelTypeBean;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setLocation(File location) {
		this.location = location.getAbsolutePath();
	}

	public ModelNode getModelNode() {
		return new ModelNode(modelTypeBean.getNode());
	}
	
	@Override
	public void setModelTypeBean(ModelTypeBean modelTypeBean) {
		this.modelTypeBean = modelTypeBean;
	}
}
