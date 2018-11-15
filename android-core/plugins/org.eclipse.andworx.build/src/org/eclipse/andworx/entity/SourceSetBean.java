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
package org.eclipse.andworx.entity;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.model.SourcePath;

/**
 * A SourceSet represents a logical group of Java source and resources.
 * TODO - Filtering
 */
@Entity(name="tableSourceSet")
public class SourceSetBean implements SourcePath {

	/** We use this field-name so we can query for projects with a certain id */
	public final static String ID_FIELD_NAME = "id";
    /** Column name in join table for project foreign key */
	public final static String ANDROID_SOURCE_ID_FIELD_NAME = "android_source_id";
	
	public static List<String> EMPTY_LIST = Collections.emptyList();

	/** This id is generated by the database and set on the object when it is passed to the create method */
    @Id @GeneratedValue
 	int id;

	/** This is a foreign object which just stores the id from the AndroidSource object in this table. */
    @OneToOne
    @JoinColumn(name=ANDROID_SOURCE_ID_FIELD_NAME, referencedColumnName="id")
	AndroidSourceBean androidSourceBean;

	/** FilterPatterns belonging to this SourceSet. The association is uni-directional, and the SourceSet "owns" it */
    @OneToMany(fetch=FetchType.EAGER)
    Collection<FilterPatternBean> filterPatternBeans;
    
    @Column
    CodeSource codeSource;
    @Column
    String path;
    
    @Transient
    List<String> includes;
    @Transient
    List<String> excludes;
 
    /**
     * Construct SourceSetBean object
     * @param codeSource CodeSource enum
     * @param path Source path
     */
    public SourceSetBean(CodeSource codeSource, String path)  {
    	this.codeSource = codeSource;
    	this.path = path;
    }
    
	/**
	 * Mandatory for OrmLite
	 */
    SourceSetBean() {
    }

    /**
     * Set entity bean of AndroidSource owner
     * @param androidSourceBean
     */
    public void setAndroidSourceBean(AndroidSourceBean androidSourceBean) {
    	this.androidSourceBean = androidSourceBean;
    }
    
	@Override
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

    @Override
	public CodeSource getCodeSource() {
		return codeSource;
	}

	@Override
	public List<String> getIncludes() {
		if (includes == null)
			return EMPTY_LIST;
		return includes;
	}

	@Override
	public List<String> getExcludes() {
		if (excludes == null)
			return EMPTY_LIST;
		return excludes;
	}

    
}