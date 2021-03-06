/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.entity;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import org.eclipse.andworx.model.FieldName;
import com.android.builder.model.BaseConfig;

/**
 * BaseConfig String fields
 */
@Entity(name="tableBaseString")
public class BaseString {
	@Column
	FieldName fieldName;
	@Column
	String value;
	
    /** Column name in join table for base config foreign key */
	public final static String BASE_CONFIG_ID_FIELD_NAME = "base_config_id";

	/** We use this field-name so we can query for flavor strings with a certain id */
	public final static String ID_FIELD_NAME = "id";

	/** This id is generated by the database and set on the object when it is passed to the create method */
    @Id @GeneratedValue
 	int id;

	/** This is a foreign object which just stores the id from the base config object in this table. */
    @OneToOne
    @JoinColumn(name=BASE_CONFIG_ID_FIELD_NAME, referencedColumnName="id")
    BaseConfigBean baseConfigBean;

    /**
     * Construct BaseString object
     * @param fieldName FieldName enum
     * @param value String value 
     */
	public BaseString(FieldName fieldName, String value) {
		this.value = value;
		this.fieldName = fieldName;
	}

	/**
	 * Mandatory for OrmLite
	 */
	BaseString() {
	}

	/**
	 * Set the entity bean of the BaseConfig owner
	 * @param baseConfigBean BaseConfigBean object
	 */
	public void setBaseConfigBean(BaseConfigBean baseConfigBean) {
		this.baseConfigBean = baseConfigBean;
	}
	
	public String getValue() {
		return value;
	}

	public FieldName getFieldName() {
		return fieldName;
	}

	public BaseConfig getBaseConfigBean() {
		return baseConfigBean;
	}

	@Override
	public int hashCode() {
		return Objects.hash(fieldName, value, baseConfigBean); 
	}

	@Override
	public boolean equals(Object object) {
		if ((object == null) || !(object instanceof BaseString))
			return false;
		BaseString other = (BaseString)object;
		return fieldName == other.fieldName && value.equals(other.value) && baseConfigBean.equals(other.baseConfigBean) ;
	}
}
