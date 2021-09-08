package com.onyx.persistence.query

import com.onyx.buffer.BufferStreamable
import com.onyx.extension.getFunctionWithinSelection
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.manager.PersistenceManager
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

/**
 * This class contains all of the information needed to execute a query including criteria, sort order information, limited selection, and row constraints.
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 * factory.setCredentials("username", "password");
 * factory.setLocation("/MyDatabaseLocation")
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
 * query.setFirstRow(100);
 * query.setMaxResults(1000);
 *
 * List results = manager.executeQuery(query);
 *
 * factory.close(); //Close the in memory database
 *
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 *
 *
 * @see PersistenceManager.executeUpdate
 * @see PersistenceManager.executeDelete
 * @see PersistenceManager.executeQuery
 */
class Query : BufferStreamable {

    /**
     * Gets the selection fields used to limit the result fields when doing a fetch
     * @since 1.0.0
     */
    var selections: List<String>? = null

    /**
     * Setter for var args
     */
    @Suppress("UNUSED")
    fun selections(vararg selections: String) {
        this.selections = listOf(*selections)
    }

    var groupBy: List<String>? = null

    /**
     * Setter for var args group by
     *
     * These are used to group results by field
     * @since 2.1.0
     */
    fun groupBy(vararg groups: String) {
        this.groupBy = listOf(*groups)
    }

    /**
     * Sets the update instructions
     * @since 1.0.0
     */
    var updates: List<AttributeUpdate> = arrayListOf()

    /**
     * Setter for updates using var args
     */
    @Suppress("UNUSED")
    fun updates(vararg updates: AttributeUpdate) {
        this.updates = listOf(*updates)
    }

    /**
     * Setter for updates using var args
     */
    @Suppress("UNUSED")
    fun order(vararg updates: QueryOrder) {
        this.queryOrders = listOf(*updates)
    }

    /**
     * Gets the criteria value that is used to filter when doing a fetch
     * @since 1.0.0
     */
    var criteria: QueryCriteria? = null

    /**
     * Sets the queryOrder objects that are used to sort the result set
     * @since 1.0.0
     */
    var queryOrders: List<QueryOrder>? = null

    /**
     * The base entity type to query upon
     * @since 1.0.0
     */
    var entityType: Class<*>? = null

    /**
     * Sets the first row of records to return a subset of results
     * @since 1.0.0
     */
    var firstRow = 0

    /**
     * Sets the maximum number of results to return
     * @since 1.0.0
     */
    var maxResults = -1

    /**
     * Set the result count of a query after executing query
     *
     * @since 1.0.0
     */
    var resultsCount: Int = 0

    /**
     * Whether to select unique row results.  This is default to true.  This does not have
     * an effect on entity queries since entity queries are distinct by default.
     *
     * @since 1.3.1 - Feature added to apply distinct rows for queries that define selections
     */
    var isDistinct: Boolean = false

    /**
     * Set the partition ID to query from
     *
     * @since 1.0.0
     */
    var partition: Any = ""

    /**
     * Specify not to use query cache
     *
     * @since 2.2.0
     */
    var nocache: Boolean = false

    /**
     * Indicates whether the query was terminated
     */
    var isTerminated: Boolean = false


    @Transient
    private var functions: List<QueryFunction>? = null

    /**
     * Get the functions associated to the query selection
     *
     * @since 2.1.0
     */
    fun functions(): List<QueryFunction> {
        if (functions == null) {
            functions = selections?.filter {
                it.getFunctionWithinSelection() != null
            }?.map {
                it.getFunctionWithinSelection()!!
            } ?: ArrayList()
        }

        return functions ?: ArrayList()
    }

    /**
     * Constructor creates an empty Query value
     * @since 1.0.0
     */
    constructor()

    /**
     * Constructor creates a query value and initializes the criteria value used for filtering results
     * @since 1.0.0
     *
     * @param entityType Managed Entity Type
     * @param criteria   Query filter criteria
     *
     *
     * Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, criteria: QueryCriteria) {
        this.entityType = entityType
        this.criteria = criteria
    }

    /**
     * Constructor creates a query value and initializes the criteria value used for filtering results
     *
     * @since 1.0.0
     *
     * @param entityType Managed Entity Type
     * @param criteria   Query filter criteria
     * @param queryOrder order by field and direction
     * `
     *
     * Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key", new QueryOrder("firstName"));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, criteria: QueryCriteria, queryOrder: QueryOrder) {
        this.entityType = entityType
        this.criteria = criteria
        this.queryOrders = listOf(queryOrder)
    }

    /**
     * Constructor creates a query value and initializes the queryOrders used for sorting results
     *
     * @since 1.0.0
     *
     * @param entityType Managed Entity Type
     * @param queryOrder order by field and direction
     *
     *
     * Query query = new Query(MyEntity.class, new QueryOrder("firstName"));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, queryOrder: QueryOrder) {
        this.entityType = entityType
        this.queryOrders = listOf(queryOrder)
    }

    /**
     * Constructor creates a query value and initializes the queryOrders used for sorting results
     *
     * @since 1.0.0
     *
     * @param entityType  Managed Entity Type
     * @param queryOrders list of queryOrders to order by multiple fields and directions
     *
     * Query query = new Query(MyEntity.class, Arrays.asList(new QueryOrder("firstName")));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, queryOrders: List<QueryOrder>) {
        this.entityType = entityType
        this.queryOrders = queryOrders
    }

    /**
     * Constructor creates a query value and initializes the queryOrders used for sorting results
     *
     * @since 1.0.0
     *
     * @param entityType  Managed Entity Type
     * @param queryOrders list of queryOrders to order by multiple fields and directions
     *
     * Query query = new Query(MyEntity.class, Arrays.asList(new QueryOrder("firstName")));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, vararg queryOrders: QueryOrder) {
        this.entityType = entityType
        this.queryOrders = listOf(*queryOrders)
    }

    /**
     * Constructor creates a query value and initializes the criteria used for filtering and the queryOrders used for sorting results
     *
     *
     * @since 1.0.0
     *
     * @param entityType  Managed Entity Type
     * @param criteria    criteria used to filter results
     * @param queryOrders list of queryOrders to order by multiple fields and directions
     *
     *
     * Query query = new Query(MyEntity.class, Arrays.asList(new QueryOrder("firstName")));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, criteria: QueryCriteria, vararg queryOrders: QueryOrder) {
        this.entityType = entityType
        this.criteria = criteria
        this.queryOrders = listOf(*queryOrders)
    }

    /**
     * Constructor creates a query value and initializes the criteria value used for filtering results along with a list of selection fields
     * @since 1.0.0
     * @param entityType Managed Entity Type
     * @param selections List of attributes to return in query results
     * @param criteria   Query filter criteria
     *
     * Query query = new Query(MyEntity.class, Arrays.asList("name", "description"), new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, selections: List<String>, criteria: QueryCriteria) {
        this.entityType = entityType
        this.selections = selections
        this.criteria = criteria
    }

    /**
     * Constructor creates a query value and initializes the criteria value used for filtering results along with an array of update details. The array of sections fields are parameters 2..n
     * @since 1.0.0
     * @param entityType Managed Entity Type
     * @param criteria   Query filter criteria
     * @param updates    Array of attribute update instructions
     *
     * Query query = new Query(Person.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"), new AttributeUpdate("name", "Jim");
     * List results = manager.executeUpdate(query);
     */
    constructor(entityType: Class<*>, criteria: QueryCriteria, vararg updates: AttributeUpdate) {
        this.entityType = entityType
        this.updates = arrayListOf(*updates)
        this.criteria = criteria
    }

    /**
     * creates a query value and initializes the criteria value used for filtering results along with a list of updates that can be used to update all rows returned from the query
     * @since 1.0.0
     *
     * Query query = new Query(Person.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"), Arrays.asList(new AttributeUpdate("name", "Jim"));
     * List results = manager.executeUpdate(query);
     *
     * @param entityType Entity Type
     * @param criteria   Query filter criteria
     * @param updates    List of attribute update instructions
     */
    constructor(entityType: Class<*>, criteria: QueryCriteria, updates: List<AttributeUpdate>) {
        this.entityType = entityType
        this.criteria = criteria
        this.updates = updates
    }

    /**
     * Constructor creates a query value and initializes the criteria value used for filtering results along with a list of selection fields and a list of queryOrders that can be used for sorting
     * @since 1.0.0
     * @param entityType  Entity Type
     * @param selections  List of attributes to return in query results
     * @param criteria    Query filter criteria
     * @param queryOrders Query Sort Order
     *
     * Query query = new Query(Person.class,
     * Arrays.asList("name"),
     * new QueryCriteria("attributeName"),
     * QueryCriteriaOperator.EQUAL, "key"),
     * Arrays.asList(new QueryOrder("name", true));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, selections: List<String>, criteria: QueryCriteria, queryOrders: List<QueryOrder>) {
        this.entityType = entityType
        this.selections = selections
        this.criteria = criteria
        this.queryOrders = queryOrders
    }

    /**
     * Constructor creates a query value and initializes the criteria value used for filtering results along with a list of selection fields and a list of queryOrders that can be used for sorting
     * @since 1.0.0
     * @param entityType Entity Type
     * @param selections List of attributes to return in query results
     * @param criteria   Query filter criteria
     *
     * Query query = new Query(Person.class,
     * Arrays.asList("name"),
     * new QueryCriteria("attributeName"),
     * QueryCriteriaOperator.EQUAL, "key"),
     * Arrays.asList(new QueryOrder("name", true));
     * List results = manager.executeQuery(query);
     *
     */
    constructor(entityType: Class<*>, criteria: QueryCriteria, vararg selections: String) {
        this.entityType = entityType
        this.selections = listOf(*selections)
        this.criteria = criteria
    }

    /**
     * This method will allow subscribers to query results.  If a record that matches the query criteria is either
     * added, updated, or removed.  The query listener will be invoked. Note, if you use this, do not forget to remove
     * the listeners when done.  If you fail to remove the listener that could degrade performance.
     *
     * This is compatible with the remote persistence manager, embedded persistence manager, and the in memory persistence manager
     *
     * Simple usage is
     *
     *
     * Query query = new Query(SystemEntity.class);
     * query.setCriteria(new QueryCriteria("id", QueryCriteriaOperator.NOT_EQUAL, 2));
     * query.setChangeListener(new QueryListener() {
     * @Override
     * public void onItemUpdated(IManagedEntity items) {
     * ...
     * }
     *
     * @Override
     * public void onItemAdded(IManagedEntity items) {
     *
     * }
     *
     * @Override
     * public void onItemRemoved(IManagedEntity items) {
     *
     * }
     * });
     *
     * @since 1.3.0
     *
     */
     var changeListener: QueryListener<*>? = null

    // region Criteria Sorting

    /**
     * Getter for all criteria.  This is stored within an unordered list.
     * It was added so the scanners can check the entire list of criteria
     * rather than walking through the root level.
     *
     * @return Unordered set of Query Criteria
     */
    @Transient
    private var allCriteriaValue:Array<QueryCriteria>? = null

    /**
     * Getter for all criteria
     */
    fun getAllCriteria():Array<QueryCriteria> {
        if(allCriteriaValue == null || allCriteriaValue!!.isEmpty()) {
            allCriteriaValue = aggregateCriteria(this.criteria, LinkedHashSet()).toTypedArray() // Typed array so the iteration is quicker
        }
        return allCriteriaValue!!
    }

    /**
     * This method aggregates the list of criteria and sub criteria into
     * a single list so that it does not have to be done upon checking criteria
     * each iteration
     *
     * @param criteria Root Criteria
     * @param allCriteria Maintained list of all criteria
     * @return List of all criteria
     *
     * @since 1.3.0 Re-vamped criteria checking to address bugs and maintain
     * record insertion criteria checking
     */
    private fun aggregateCriteria(criteria: QueryCriteria?, allCriteria: MutableSet<QueryCriteria>): Set<QueryCriteria> {
        if (criteria == null)
            return allCriteria

        allCriteria.add(criteria)

        for (subCriteria in criteria.subCriteria) {
            aggregateCriteria(subCriteria, allCriteria)
            subCriteria.parentCriteria = criteria

            // This indicates it is a root criteria.  In that case, we need to
            // look at the first sub criteria and assign its modifier
            if (!criteria.isAnd && !criteria.isOr) {
                if (subCriteria.isOr)
                    criteria.isOr = true
                else
                    criteria.isAnd = true
            }
        }

        return allCriteria
    }

    // endregion

    fun shouldSortResults(): Boolean = this.queryOrders != null && this.queryOrders!!.isNotEmpty()

    @Transient
    internal var isUpdateOrDelete:Boolean = false

    @Transient
    var isLazy:Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Query

        if (selections != other.selections) return false
        if (criteria != other.criteria) return false
        if (queryOrders != other.queryOrders) return false
        if (entityType != other.entityType) return false
        if (isDistinct != other.isDistinct) return false
        if (partition != other.partition) return false
        if (nocache != other.nocache) return false

        return true
    }

    override fun hashCode(): Int {
        var result = selections?.hashCode() ?: 0
        result = 31 * result + (criteria?.hashCode() ?: 0)
        result = 31 * result + (queryOrders?.hashCode() ?: 0)
        result = 31 * result + (entityType?.hashCode() ?: 0)
        result = 31 * result + isDistinct.hashCode()
        result = 31 * result + partition.hashCode()
        result = 31 * result + nocache.hashCode()
        return result
    }
}
