package com.onyx.persistence.query

import com.onyx.buffer.BufferStreamable
import com.onyx.persistence.manager.PersistenceManager

/**
 * The purpose of this is to specify the query sort order while querying.
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
 * query.setQueryOrders(new QueryOrder("name", true)
 *
 * List results = manager.executeQuery(query);
 *
 * @see com.onyx.persistence.query.Query
 * @see PersistenceManager.executeQuery
 */
data class QueryOrder @JvmOverloads constructor (var attribute: String = "", var isAscending:Boolean = true) : BufferStreamable
