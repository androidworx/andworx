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
package org.eclipse.andworx.jpa;

import java.util.List;

import javax.persistence.Query;

import au.com.cybersearch2.classyjpa.EntityManagerLite;
import au.com.cybersearch2.classyjpa.entity.PersistenceTask;

/**
 * Task to perform a named query and store the result
 * @param <E> Entity type 
 * @param <E> Selection type 
 */
public class ListQueryTask<E,S> implements PersistenceTask {

	/** Return list */
	List<E> resultList;
	/** Name of query */
	private final String queryName; 
	/** Column name being selected */
	private final String columnName; 
	/** Value to match on */
	private final S value;

	/**
	 * Construct QueryTask object. (Value type may be parametized in the future)
	 * @param queryName Name of query
	 * @param columnName Column name being selected
	 * @param value Value to match on
	 */
	public ListQueryTask(String queryName, String columnName, S value) {
		this.queryName = queryName;
		this.columnName = columnName;
		this.value = value;
	}

	/**
	 * Returns the result list. Only valid after query has completed successfuly.
	 * @return List of entity type
	 */
	public List<E> getResultList() {
		return resultList;
	}
	
    @SuppressWarnings("unchecked")
	@Override
    public void doTask(EntityManagerLite entityManager)
    {
        Query query = entityManager.createNamedQuery(queryName);
        query.setParameter(columnName, value);
	    this.resultList = (List<E>) query.getResultList();
    }
}
