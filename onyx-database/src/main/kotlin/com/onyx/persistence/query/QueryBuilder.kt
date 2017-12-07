package com.onyx.persistence.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.manager.PersistenceManager
import kotlin.reflect.KClass

class QueryBuilder(var manager:PersistenceManager, var query: Query) {
    var onItemAdded:((Any) -> Unit)? = null
    var onItemDeleted:((Any) -> Unit)? = null
    var onItemUpdated:((Any) -> Unit)? = null

    // region Query Execution

    fun <T> list():List<T> {
        assignListener()
        return manager.executeQuery(this.query)
    }

    fun <T : IManagedEntity> lazy():List<T> {
        assignListener()
        return manager.executeLazyQuery(this.query)
    }

    fun update():Int {
        assignListener()
        return manager.executeUpdate(this.query)
    }

    fun count():Long {
        assignListener()
        return manager.countForQuery(this.query)
    }

    fun delete():Int {
        assignListener()
        return manager.executeDelete(this.query)
    }

    /**
     * Stop Listening.  This method will stop listening on changes that match the specified query
     *
     * @since 2.0.0
     */
    fun stopListening():QueryBuilder {
        manager.removeChangeListener(query)
        return this
    }

    /**
     * Listen to query changes using the onItemAdded, onItemRemoved, and onItemDeleted closure methods
     *
     * @since 2.0.0
     */
    fun listen():QueryBuilder {
        this.assignListener()
        manager.listen(query)
        return this
    }

    /**
     * Listen to query changes using the Query Listener implementation
     *
     * @since 2.0.0
     */
    fun <T> listen(listener: QueryListener<T>):QueryBuilder {
        this.query.changeListener = listener
        manager.listen(query)
        return this
    }

    private fun assignListener() {
        if(onItemAdded != null
                || onItemDeleted != null
                || onItemUpdated != null) {
            this.query.changeListener = object : QueryListener<Any> {
                override fun onItemUpdated(item: Any) {
                    onItemUpdated?.invoke(item)
                }

                override fun onItemAdded(item: Any) {
                    onItemAdded?.invoke(item)
                }

                override fun onItemRemoved(item: Any) {
                    onItemDeleted?.invoke(item)
                }
            }
        }
    }

    // endregion

    // region Query Building

    fun from(type:KClass<*>): QueryBuilder {
        this.query.entityType = type.javaObjectType
        return this
    }

    fun where(criteria: QueryCriteria): QueryBuilder {
        this.query.criteria = criteria
        return this
    }

    fun and(criteria: QueryCriteria): QueryBuilder {
        this.query.criteria?.and(criteria)
        return this
    }

    fun or(criteria: QueryCriteria): QueryBuilder {
        this.query.criteria?.or(criteria)
        return this
    }

    fun limit(limit:Int): QueryBuilder {
        this.query.maxResults = limit
        return this
    }

    fun first(first:Int): QueryBuilder {
        this.query.firstRow = first
        return this
    }

    fun set(vararg update:AttributeUpdate): QueryBuilder {
        this.query.updates = update.toList()
        return this
    }

    // endregion

    // region Query Order

    fun <T> orderBy(vararg order:T): QueryBuilder {
        query.queryOrders = ArrayList()
        order.toList().forEach {
            if(it is QueryOrder)
                (query.queryOrders as MutableList).add(it)
            else if(it is String) {
                (query.queryOrders as MutableList).add(QueryOrder(it))
            }
        }
        return this
    }

    // endregion

    // region Listener events

    @Suppress("UNCHECKED_CAST")
    fun <T : IManagedEntity> onItemAdded(listener:((T) -> Unit)): QueryBuilder {
        this.onItemAdded = listener as ((Any) -> Unit)?
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : IManagedEntity> onItemDeleted(listener:((T) -> Unit)): QueryBuilder {
        this.onItemDeleted = listener as ((Any) -> Unit)?
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : IManagedEntity> onItemUpdated(listener:((T) -> Unit)): QueryBuilder {
        this.onItemUpdated = listener as ((Any) -> Unit)?
        return this
    }

    // endregion
}

// region Query Builder Construction Extensions

fun PersistenceManager.from(type:KClass<*>): QueryBuilder = QueryBuilder(this, Query(type.javaObjectType))

fun PersistenceManager.select(vararg properties:String): QueryBuilder {
    val query = Query()
    query.selections = properties.toList()
    return QueryBuilder(this, query)
}

// endregion

// region Query Criteria Operators

infix fun <T> String.eq(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.EQUAL, value)
infix fun <T> String.neq(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_EQUAL, value)
infix fun <T> String.notIn(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_IN, value)
@Suppress("FunctionName")
infix fun <T> String.IN(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.IN, value)
infix fun <T> String.gte(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN_EQUAL, value)
infix fun <T> String.gt(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN, value)
infix fun <T> String.lte(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LESS_THAN_EQUAL, value)
infix fun <T> String.lt(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LESS_THAN, value)
infix fun <T> String.match(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.MATCHES, value)
@Suppress("UNUSED")
infix fun <T> String.notMatch(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_MATCHES, value)
infix fun <T> String.like(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LIKE, value)
@Suppress("UNUSED")
infix fun <T> String.notLike(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_LIKE, value)
infix fun <T> String.cont(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.CONTAINS, value)
infix fun <T> String.notCont(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS, value)
infix fun <T> String.startsWith(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.STARTS_WITH, value)
infix fun <T> String.notStartsWith(value:T):QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_STARTS_WITH, value)
fun String.notNull():QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_NULL)
@Suppress("UNUSED")
fun String.isNull():QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.IS_NULL)

// endregion

// region Query Update

infix fun String.to(value: Any?) = AttributeUpdate(this, value)

// endregion

// region Query Order Extensions

fun String.asc():QueryOrder = QueryOrder(this, true)
fun String.desc():QueryOrder = QueryOrder(this, false)

// endregion
