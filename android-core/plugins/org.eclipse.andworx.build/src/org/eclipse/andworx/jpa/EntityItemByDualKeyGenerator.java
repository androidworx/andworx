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

import java.sql.SQLException;

import com.j256.ormlite.stmt.QueryBuilder;

import au.com.cybersearch2.classyjpa.entity.PersistenceDao;
import au.com.cybersearch2.classyjpa.query.DaoQuery;
import au.com.cybersearch2.classyjpa.query.DaoQueryFactory;
import au.com.cybersearch2.classyjpa.query.DaoQuery.SimpleSelectArg;

/**
 * Generate query to find an entity object in a collection by container key then item key
 */
public class EntityItemByDualKeyGenerator implements DaoQueryFactory {

	private String containerColumn;
	private String itemColumn;

	/**
	 * Construct EntityItemByDualKeyGenerator object
	 * @param containerColumn Name of container column to match on
	 * @param itemColumn Name of item column to match on
	 */
	public EntityItemByDualKeyGenerator(String containerColumn, String itemColumn) {
		this.containerColumn = containerColumn;
		this.itemColumn = itemColumn;
	}
	
   /**
     * Generate query to find an entity object in a collection by container key then item key
     * @see au.com.cybersearch2.classyjpa.query.DaoQueryFactory#generateQuery(au.com.cybersearch2.classyjpa.entity.PersistenceDao)
     */
    @Override
    public <T> DaoQuery<T> generateQuery(PersistenceDao<T, ?> dao)
            throws SQLException 
    {   // Two select arguments required
        final SimpleSelectArg containerKeyArg = new SimpleSelectArg();
        // Set primary key column name
        containerKeyArg.setMetaInfo(containerColumn);
        final SimpleSelectArg itemKeyArg = new SimpleSelectArg();
        // Set primary key column name
        itemKeyArg.setMetaInfo(itemColumn);
        return new DaoQuery<T>(dao, containerKeyArg, itemKeyArg){

            /**
             * Update supplied QueryBuilder object to add where clause
             * @see au.com.cybersearch2.classyjpa.query.DaoQuery#buildQuery(com.j256.ormlite.stmt.QueryBuilder)
             */
            @Override
            protected QueryBuilder<T, ?> buildQuery(QueryBuilder<T, ?> queryBuilder)
                    throws SQLException {
                // build a query with the WHERE clause set to 'id = ?'
                queryBuilder.where().eq(containerColumn, containerKeyArg).and().eq(itemColumn, itemKeyArg);
                return queryBuilder;
            }};
    }

}
