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
 * Generate query to find an entity object by secondary key.
 * There are 2 select arguments, the column name and value on which to match 
 */
public class EntityBySecondaryKeyGenerator implements DaoQueryFactory {

	private String column;

	/**
	 * Construct EntityBySecondaryKeyGenerator object
	 * @param column Name of column to match on
	 */
	public EntityBySecondaryKeyGenerator(String column) {
		this.column = column;
	}
	
    @Override
    public <T> DaoQuery<T> generateQuery(PersistenceDao<T, ?> dao)
            throws SQLException 
    {   // Only one select argument required for secondary key 
        final SimpleSelectArg secondaryKeyArg = new SimpleSelectArg();
        // Set primary key column name
        secondaryKeyArg.setMetaInfo(column);
        return new DaoQuery<T>(dao, secondaryKeyArg) {

            /**
             * Update supplied QueryBuilder object to add where clause
             * @see au.com.cybersearch2.classyjpa.query.DaoQuery#buildQuery(com.j256.ormlite.stmt.QueryBuilder)
             */
            @Override
            protected QueryBuilder<T, ?> buildQuery(QueryBuilder<T, ?> queryBuilder)
                    throws SQLException {
                // build a query with the WHERE clause set to 'column = ?'
                queryBuilder.where().eq(column, secondaryKeyArg);
                return queryBuilder;
            }};
    }
}
