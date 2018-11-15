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
 * Generate query to find an entity object by project ID
 */
public class EntityByProjectIdGenerator implements DaoQueryFactory {
    /**
     * Generate query to find entity by primary key
     * @see au.com.cybersearch2.classyjpa.query.DaoQueryFactory#generateQuery(au.com.cybersearch2.classyjpa.entity.PersistenceDao)
     */
    @Override
    public <T> DaoQuery<T> generateQuery(PersistenceDao<T, ?> dao)
            throws SQLException 
    {   // Only one select argument required for primary key 
        final SimpleSelectArg joinKeyArg = new SimpleSelectArg();
        // Set primary key column name
        joinKeyArg.setMetaInfo("project_id");
        return new DaoQuery<T>(dao, joinKeyArg){

            /**
             * Update supplied QueryBuilder object to add where clause
             * @see au.com.cybersearch2.classyjpa.query.DaoQuery#buildQuery(com.j256.ormlite.stmt.QueryBuilder)
             */
            @Override
            protected QueryBuilder<T, ?> buildQuery(QueryBuilder<T, ?> queryBuilder)
                    throws SQLException {
                // build a query with the WHERE clause set to 'id = ?'
                queryBuilder.where().eq("project_id", joinKeyArg);
                return queryBuilder;
            }};
    }

}
