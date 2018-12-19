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

import java.net.MalformedURLException;
import java.net.URL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import org.eclipse.andworx.model.RepositoryUrl;
import org.eclipse.andworx.topology.NodeElement;

/**
 * Repository name and URL attached to ModelNode tree
 */
@Entity(name = "tableRepositories")
public class RepositoryBean implements RepositoryUrl, NodeElement {
	
    /** Column name in join table for repository foreign key */
	public final static String REPOSITORY_ID_FIELD_NAME = "id";
	public final static String MODEL_TYPE_ID_FIELD_NAME = "model_type_id";

	@Id @GeneratedValue
    int id;

    @Column(nullable = false)
    String name;
    
    @Column(nullable = false)
    String url;
    
    @OneToOne
    @JoinColumn(name=MODEL_TYPE_ID_FIELD_NAME, referencedColumnName="id")
    ModelTypeBean modelTypeBean;
    
    RepositoryBean() {
    }

    public RepositoryBean(String name, URL url) {
    	this.name = name;
    	this.url = url.toExternalForm();
    }

	public String getName() {
		return name;
	}

	public String getUrlValue() {
		return url;
	}

	public URL getUrl() throws MalformedURLException {
		return new URL(url);
	}
	
	public ModelTypeBean getModelTypeBean() {
		return modelTypeBean;
	}

	@Override
	public void setModelTypeBean(ModelTypeBean modelTypeBean) {
		this.modelTypeBean = modelTypeBean;
	}
}
