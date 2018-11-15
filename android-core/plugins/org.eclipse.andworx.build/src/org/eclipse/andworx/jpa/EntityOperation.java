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

import au.com.cybersearch2.classyjpa.EntityManagerLite;
import au.com.cybersearch2.classyjpa.entity.PersistenceTask;

/**
 * Persistent task utility to Facilitate simple operations such as persist and delete
 */
public class EntityOperation {
	
	/**
	 * Returns task to persist given entity object
	 * @param entity 
	 * @return PersistenceTask object
	 */
	public <T> PersistenceTask persist(T entity) {
        return new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	entityManager.persist(entity);
            }};
	}

	/**
	 * Returns task to remove given entity object
	 * @param entity 
	 * @return PersistenceTask object
	 */
	public <T> PersistenceTask delete(T entity) throws InterruptedException {
        return new PersistenceTask(){

            @Override
            public void doTask(EntityManagerLite entityManager)
            {
            	entityManager.merge(entity);
            	entityManager.remove(entity);
            }
        };
 	}

}


