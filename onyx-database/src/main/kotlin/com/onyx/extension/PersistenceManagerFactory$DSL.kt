package com.onyx.extension

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import kotlin.reflect.KClass

class QueryBuilder(var manager:PersistenceManager, var query: Query)

// region Query Construction

fun PersistenceManager.from(type:KClass<*>):QueryBuilder = QueryBuilder(this, Query(type.javaObjectType))

fun PersistenceManager.select(vararg properties:String):QueryBuilder {
    val query = Query()
    query.selections = properties.toList()
    return QueryBuilder(this, query)
}

// endregion

// region Query Building

fun QueryBuilder.from(type:KClass<*>):QueryBuilder {
    this.query.entityType = type.javaObjectType
    return this
}

fun QueryBuilder.where(criteria: QueryCriteria): QueryBuilder {
    this.query.criteria = criteria
    return this
}

fun QueryBuilder.limit(limit:Int):QueryBuilder {
    this.query.maxResults = limit
    return this
}

fun QueryBuilder.first(first:Int):QueryBuilder {
    this.query.firstRow = first
    return this
}

fun QueryBuilder.set(vararg update:AttributeUpdate):QueryBuilder {
    this.query.updates = update.toList()
    return this
}

// endregion

// region Query Execution

fun <T> QueryBuilder.list() = manager.executeQuery<T>(this.query)
fun <T : IManagedEntity> QueryBuilder.lazy() = manager.executeLazyQuery<T>(this.query)
fun QueryBuilder.update() = manager.executeUpdate(this.query)
fun QueryBuilder.count() = manager.countForQuery(this.query)
fun QueryBuilder.delete() = manager.executeDelete(this.query)

// endregion

// region Query Criteria Joins

infix fun QueryCriteria.and(criteria:QueryCriteria):QueryCriteria {
    this.and(criteria)
    return this
}

infix fun QueryCriteria.or(criteria: QueryCriteria): QueryCriteria {
    this.or(criteria)
    return this
}

// endregion

// region Query Criteria Operators

infix fun <T> String.eq(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.EQUAL, value)
infix fun <T> String.neq(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_EQUAL, value)
infix fun <T> String.notIn(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_IN, value)
infix fun <T> String.IN(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.IN, value)
infix fun <T> String.gte(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN_EQUAL, value)
infix fun <T> String.gt(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN, value)
infix fun <T> String.lte(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LESS_THAN_EQUAL, value)
infix fun <T> String.lt(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LESS_THAN, value)
infix fun <T> String.match(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.MATCHES, value)
infix fun <T> String.notMatch(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_MATCHES, value)
infix fun <T> String.like(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LIKE, value)
infix fun <T> String.notLike(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_LIKE, value)
infix fun <T> String.cont(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.CONTAINS, value)
infix fun <T> String.notCont(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS, value)
infix fun <T> String.startsWith(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.STARTS_WITH, value)
infix fun <T> String.notStartsWith(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_STARTS_WITH, value)
fun String.notNull():QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_NULL)
fun String.isNull():QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.IS_NULL)

operator fun QueryCriteria.not() {
    this.not()
}

// endregion

// region Query Update

infix fun String.to(value: Any?) = AttributeUpdate(this, value)

// endregion
