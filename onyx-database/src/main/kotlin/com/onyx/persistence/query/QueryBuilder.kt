package com.onyx.persistence.query

import com.onyx.entity.SystemEntity
import com.onyx.extension.common.async
import com.onyx.extension.common.get
import com.onyx.extension.identifier
import com.onyx.interactors.record.FullTextRecordInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.stream.QueryMapStream
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

class QueryBuilder(
    @Transient
    var manager: PersistenceManager, var query: Query
) {
    var onItemAdded: ((Any) -> Unit)? = null
    var onItemDeleted: ((Any) -> Unit)? = null
    var onItemUpdated: ((Any) -> Unit)? = null

    // region Query Execution

    fun <T> list(): List<T> {
        assignListener()
        return manager.executeQuery(this.query)
    }

    fun <T : IManagedEntity> stream(): List<T> {
        val results = ArrayList<T>()
        manager.stream<T>(this.query) {
            results.add(it)
            results.size < query.maxResults
        }
        return results
    }

    fun <T : IManagedEntity> stream(action: (T) -> Boolean) {
        var i = 0
        manager.stream<T>(this.query) {
            action(it) && (++i < query.maxResults || query.maxResults <= 0)
        }
    }

    @Suppress("unused")
    fun <T : IManagedEntity> asSequence(
        bufferSize: Int = 5_000,
    ): Sequence<T> = Sequence {

        /* ── 1. queue & sentinel ─────────────────────────────────────────── */
        require(bufferSize > 0)
        val SENTINEL = Any()                           // unique end-marker
        val queue = ArrayBlockingQueue<Any>(bufferSize + 1)
        //  ^ one extra slot, so the sentinel always fits even when full

        val cancelled = AtomicBoolean(false)

        /* ── 2. background producer ─────────────────────────────────────── */
        val producer = async {
            try {
                manager.stream<T>(query) { entity ->
                    // stop fast if consumer bailed
                    if (cancelled.get()) return@stream false

                    // bounded put with timeout so we can re-check the flag
                    while (!queue.offer(entity, 400, TimeUnit.MILLISECONDS)) {
                        if (cancelled.get()) return@stream false
                    }
                    true                                // keep streaming
                }
            } finally {
                // guaranteed to succeed because of the +1 capacity
                queue.put(SENTINEL)
            }
        }

        /* ── 3. lazy iterator ───────────────────────────────────────────── */
        object : Iterator<T> {
            private var nextObj: Any = queue.take()    // prime
            private val lock = Any()

            override fun hasNext(): Boolean = synchronized(lock) {
                nextObj !== SENTINEL
            }

            @Suppress("UNCHECKED_CAST")
            override fun next(): T = synchronized(lock) {
                if (nextObj === SENTINEL) throw NoSuchElementException()

                val out = nextObj as T
                nextObj = queue.take()

                if (nextObj === SENTINEL) {
                    cancelled.set(true)
                    producer.cancel(true)
                }
                out
            }
        }
    }

    fun streamMap(action: (Map<String, Any?>) -> Boolean) {
        manager.stream(this.query, object : QueryMapStream<Map<String, Any?>> {
            override fun accept(entity: Map<String, Any?>, persistenceManager: PersistenceManager): Boolean {
                return action(entity)
            }
        })
    }

    fun <T : IManagedEntity> parallelStream(threads: Int = 10, action: (T) -> Unit) {
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        manager.stream<T>(this.query) {
            executor.submit {
                try {
                    action(it)
                } finally {
                    latch.countDown()
                }
            }
            true
        }

        latch.await()  // Wait until all tasks are finished
        executor.shutdown()  // Close the executor after all tasks are complete
    }


    fun <T : IManagedEntity> lazy(): List<T> {
        assignListener()
        return manager.executeLazyQuery(this.query)
    }

    fun <T> forEach(unit: (T) -> Unit) {
        list<T>().forEach { unit.invoke(it) }
    }

    fun <T, R> map(unit: (T) -> R): List<R> = list<T>().map { unit.invoke(it) }

    fun <T> filter(unit: (T) -> Boolean) = list<T>().filter(unit)

    fun update(): Int {
        assignListener()
        return manager.executeUpdate(this.query)
    }

    fun count(): Long {
        assignListener()
        return manager.countForQuery(this.query)
    }

    fun delete(): Int {
        assignListener()
        return manager.executeDelete(this.query)
    }

    /**
     * Stop Listening.  This method will stop listening on changes that match the specified query
     *
     * @since 2.0.0
     */
    fun stopListening(): QueryBuilder {
        manager.removeChangeListener(query)
        return this
    }

    /**
     * Listen to query changes using the onItemAdded, onItemRemoved, and onItemDeleted closure methods
     *
     * @since 2.0.0
     */
    fun listen(): QueryBuilder {
        this.assignListener()
        manager.listen(query)
        return this
    }

    /**
     * Listen to query changes using the Query Listener implementation
     *
     * @since 2.0.0
     */
    fun <T> listen(listener: QueryListener<T>): QueryBuilder {
        this.query.changeListener = listener
        manager.listen(query)
        return this
    }

    private fun assignListener() {
        if (onItemAdded != null
            || onItemDeleted != null
            || onItemUpdated != null
        ) {
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

    fun from(type: KClass<*>): QueryBuilder {
        this.query.entityType = type.javaObjectType
        return this
    }

    inline fun <reified T> from(): QueryBuilder {
        val type = T::class
        this.query.entityType = type.java
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

    @Suppress("unused")
    fun inPartition(partition: Any): QueryBuilder {
        this.query.partition = partition
        return this
    }

    fun limit(limit: Int): QueryBuilder {
        this.query.maxResults = limit
        return this
    }

    fun first(first: Int): QueryBuilder {
        this.query.firstRow = first
        return this
    }

    fun distinct(): QueryBuilder {
        this.query.isDistinct = true
        return this
    }

    fun cache(): QueryBuilder {
        this.query.cache = true
        return this
    }

    fun groupBy(vararg properties: String): QueryBuilder {
        this.query.groupBy(*properties)
        return this
    }

    /**
     * Apply a Lucene full-text query across the entire record.
     */
    fun fullText(queryText: String, minScore: Float? = null): QueryBuilder {
        val criteria = QueryCriteria(
            Query.FULL_TEXT_ATTRIBUTE,
            QueryCriteriaOperator.MATCHES,
            FullTextQuery(queryText, minScore)
        )
        if (query.criteria == null) {
            query.criteria = criteria
        } else {
            query.criteria?.and(criteria)
        }
        return this
    }

    /**
     * Apply a Lucene full-text query across the entire record.
     */
    fun search(queryText: String, minScore: Float? = null): QueryBuilder = fullText(queryText, minScore)

    fun <T> first(): T {
        limit(1)
        return list<T>().first()
    }

    fun <T> firstOrNull(): T? {
        limit(1)
        return list<T>().firstOrNull()
    }

    fun set(vararg update: AttributeUpdate): QueryBuilder {
        this.query.updates += update.toList()
        return this
    }

    // endregion

    // region Query Order

    fun <T> orderBy(vararg order: T): QueryBuilder {
        query.queryOrders = ArrayList()
        order.toList().forEach {
            if (it is QueryOrder)
                (query.queryOrders as MutableList).add(it)
            else if (it is String) {
                (query.queryOrders as MutableList).add(QueryOrder(it))
            }
        }
        return this
    }

    // endregion

    // region Listener events

    @Suppress("UNCHECKED_CAST")
    fun <T : IManagedEntity> onItemAdded(listener: ((T) -> Unit)): QueryBuilder {
        this.cache()
        this.onItemAdded = listener as ((Any) -> Unit)?
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : IManagedEntity> onItemDeleted(listener: ((T) -> Unit)): QueryBuilder {
        this.cache()
        this.onItemDeleted = listener as ((Any) -> Unit)?
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : IManagedEntity> onItemUpdated(listener: ((T) -> Unit)): QueryBuilder {
        this.cache()
        this.onItemUpdated = listener as ((Any) -> Unit)?
        return this
    }

    // endregion
}

// region Query Builder Construction Extensions

fun PersistenceManager.from(type: KClass<*>): QueryBuilder = QueryBuilder(this, Query(type.javaObjectType))

inline fun <reified T> PersistenceManager.from(): QueryBuilder = from(T::class)

fun PersistenceManager.select(vararg properties: String): QueryBuilder {
    val query = Query()
    query.selections = properties.toList()
    return QueryBuilder(this, query)
}

/**
 * Execute a full-text search across all tables that support Lucene-backed record interactors.
 */
fun PersistenceManager.searchAllTables(
    queryText: String,
    limit: Int = 100,
    minScore: Float? = null
): List<FullTextSearchResult> {
    val context = this.context
    val systemEntities = context.serializedPersistenceManager
        .from(SystemEntity::class)
        .where(("isLatestVersion" eq true) and ("name" notStartsWith "com.onyx.entity.System"))
        .list<SystemEntity>()

    val results = ArrayList<FullTextSearchResult>()
    val maxResults = if (limit > 0) limit else Int.MAX_VALUE

    systemEntities.forEach { systemEntity ->
        val entityClass = runCatching { systemEntity.type(context.contextId) }
            .getOrNull()
            ?.takeIf { IManagedEntity::class.java.isAssignableFrom(it) }
            ?: return@forEach

        val descriptor = runCatching { context.getDescriptorForEntity(entityClass, "") }
            .getOrNull()
            ?: return@forEach

        if (context.getRecordInteractor(descriptor) !is FullTextRecordInteractor) {
            return@forEach
        }

        val queryBuilder = this.from(entityClass.kotlin)
            .search(queryText, minScore)

        if (limit > 0) {
            queryBuilder.limit(limit)
        }

        val tableResults = queryBuilder.list<IManagedEntity>()
        tableResults.forEach { entity ->
            if (results.size >= maxResults) return results
            results.add(FullTextSearchResult(entity.identifier(context), entityClass, entity))
        }
    }

    return results
}

/**
 * Execute a full-text search across all tables that support Lucene-backed record interactors.
 */
fun PersistenceManager.search(queryText: String, minScore: Float? = null): FullTextSearchBuilder {
    return FullTextSearchBuilder(this, queryText, minScore)
}

// endregion

// region Query Criteria Operators

infix fun <T> String.eq(value: T): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.EQUAL, value)
infix fun <T> String.neq(value: T): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_EQUAL, value)
infix fun <T> String.notIn(values: List<T>): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_IN, values)

@Suppress("FunctionName")
infix fun <T> String.IN(values: List<T>): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.IN, values)

infix fun <T> String.gte(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN_EQUAL, value)

infix fun <T> String.gt(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN, value)

infix fun <T> String.lte(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.LESS_THAN_EQUAL, value)

infix fun <T> String.lt(value: T): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LESS_THAN, value)
infix fun <T> String.match(value: T): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.MATCHES, value)
fun search(queryText: String, minScore: Float? = null): QueryCriteria {
    return QueryCriteria(
        Query.FULL_TEXT_ATTRIBUTE,
        QueryCriteriaOperator.MATCHES,
        FullTextQuery(queryText, minScore)
    )
}
infix fun <T> String.between(range: Pair<T, T>): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.BETWEEN, range)
infix fun <T> String.notBetween(range: Pair<T, T>): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_BETWEEN, range)

@Suppress("UNUSED")
infix fun <T> String.notMatch(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_MATCHES, value)

@Suppress("unused")
infix fun <T> String.like(value: T): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.LIKE, value)

@Suppress("UNUSED")
infix fun <T> String.notLike(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_LIKE, value)

infix fun <T> String.cont(value: T): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.CONTAINS, value)
infix fun <T> String.containsIgnoreCase(value: T): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.CONTAINS_IGNORE_CASE, value)
infix fun <T> String.notCont(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS, value)
infix fun <T> String.notContainsIgnoreCase(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, value)

infix fun <T> String.startsWith(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.STARTS_WITH, value)

infix fun <T> String.notStartsWith(value: T): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_STARTS_WITH, value)

fun String.notNull(): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_NULL)

@Suppress("UNUSED")
    fun String.isNull(): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.IS_NULL)

@Suppress("unused")
infix fun String.inOp(values: Any): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.IN, values)

@Suppress("unused")
infix fun String.notIn(values: Any): QueryCriteria = QueryCriteria(this, QueryCriteriaOperator.NOT_IN, values)

infix fun String.IN(values: Any): QueryCriteria = when (values) {
    is List<*> -> QueryCriteria(this, QueryCriteriaOperator.IN, values)
    else -> QueryCriteria(this, QueryCriteriaOperator.IN, values)
}

infix fun String.notIn(values: QueryBuilder): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.NOT_IN, values)

infix fun String.IN(values: QueryBuilder): QueryCriteria =
    QueryCriteria(this, QueryCriteriaOperator.IN, values)

// endregion

// region Query Update

infix fun String.to(value: Any?) = AttributeUpdate(this, value)

// endregion

// region Query Order Extensions

fun String.asc(): QueryOrder = QueryOrder(this, true)
fun String.desc(): QueryOrder = QueryOrder(this, false)

// endregion

// region Field Functions

fun avg(attribute: String) = "avg($attribute)"
fun sum(attribute: String) = "sum($attribute)"
fun std(attribute: String) = "std($attribute)"
fun median(attribute: String) = "median($attribute)"
fun variance(attribute: String) = "variance($attribute)"

fun count(attribute: String) = "count($attribute)"
fun min(attribute: String) = "min($attribute)"
fun max(attribute: String) = "max($attribute)"
fun upper(attribute: String) = "upper($attribute)"
fun lower(attribute: String) = "lower($attribute)"
fun substring(attribute: String, from: Int, length: Int) = "substring($attribute, $from, $length)"
fun replace(attribute: String, pattern: String, replace: String) = "replace($attribute, '$pattern', '$replace')"
fun percentile(attribute: String, percent: Double) = "percentile($attribute, $percent)"
fun format(attribute: String, pattern: String) = "format($attribute, '$pattern')"

// endregion

// region Query Results Extensions

fun List<IManagedEntity>.values(field: String): List<Any> = this.mapNotNull { it.get(field) }

// endregion
