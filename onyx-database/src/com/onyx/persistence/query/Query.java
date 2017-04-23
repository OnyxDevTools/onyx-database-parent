package com.onyx.persistence.query;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.update.AttributeUpdate;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.query.QueryListener;
import com.onyx.util.CompareUtil;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * This class contains all of the information needed to execute a query including criteria, sort order information, limited selection, and row constraints.
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("/MyDatabaseLocation")
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 *   Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
 *   query.setFirstRow(100);
 *   query.setMaxResults(1000);
 *
 *   List results = manager.executeQuery(query);
 *
 *   factory.close(); //Close the in memory database
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 *
 * @see PersistenceManager#executeUpdate(Query)
 * @see PersistenceManager#executeDelete(Query)
 * @see PersistenceManager#executeQuery(Query)
 */
public class Query implements ObjectSerializable, Serializable
{

    private List<String> selections;
    @SuppressWarnings("WeakerAccess")
    protected List<AttributeUpdate> updates;
    @SuppressWarnings("WeakerAccess")
    protected QueryCriteria criteria;
    private List<QueryOrder> queryOrders;

    private Class entityType;
    private int firstRow = 0;
    @SuppressWarnings("WeakerAccess")
    protected int maxResults = -1;
    private int resultsCount;
    private boolean distinct;

    /**
     * Stop query before it finishes
     * @since 1.0.0
     */
    private volatile boolean kill;

    @SuppressWarnings("WeakerAccess")
    protected Object partition = "";

    /**
     * Constructor creates an empty Query object
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public Query()
    {

    }

    /**
     * Constructor creates a query object and initializes the criteria object used for filtering results
     * @since 1.0.0
     *
     * @param entityType Managed Entity Type
     * @param criteria   Query filter criteria
     *
     * <pre>
     * <code>
     *
     *   Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
     *   List results = manager.executeQuery(query);
     *
     * </code>
     * </pre>
     * */
    public Query(Class entityType, QueryCriteria criteria)
    {
        this.entityType = entityType;
        this.criteria = criteria;
    }

    /**
     * Constructor creates a query object and initializes the criteria object used for filtering results
     *
     * @since 1.0.0
     *
     * @param entityType Managed Entity Type
     * @param criteria   Query filter criteria
     * @param queryOrder order by field and direction
     *
     * <pre>
     * <code>
     *
     *   Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key", new QueryOrder("firstName"));
     *   List results = manager.executeQuery(query);
     *
     * </code>
     * </pre>
     * */
    @SuppressWarnings("unused")
    public Query(Class entityType, QueryCriteria criteria, QueryOrder queryOrder)
    {
        this.entityType = entityType;
        this.criteria = criteria;
        this.queryOrders = Collections.singletonList(queryOrder);
    }

    /**
     * Constructor creates a query object and initializes the queryOrders used for sorting results
     *
     * @since 1.0.0
     *
     * @param entityType Managed Entity Type
     * @param queryOrder order by field and direction
     *
     * <pre>
     * <code>
     *
     *   Query query = new Query(MyEntity.class, new QueryOrder("firstName"));
     *   List results = manager.executeQuery(query);
     *
     * </code>
     * </pre>
     * */
    @SuppressWarnings("unused")
    public Query(Class entityType, QueryOrder queryOrder)
    {
        this.entityType = entityType;
        this.queryOrders = Collections.singletonList(queryOrder);
    }

    /**
     * Constructor creates a query object and initializes the queryOrders used for sorting results
     *
     * @since 1.0.0
     *
     * @param entityType  Managed Entity Type
     * @param queryOrders list of queryOrders to order by multiple fields and directions
     *
     * <pre>
     * <code>
     *
     *   Query query = new Query(MyEntity.class, Arrays.asList(new QueryOrder("firstName")));
     *   List results = manager.executeQuery(query);
     *
     * </code>
     * </pre>
     * */
    @SuppressWarnings("unused")
    public Query(Class entityType, List<QueryOrder> queryOrders)
    {
        this.entityType = entityType;
        this.queryOrders = queryOrders;
    }

    /**
     * Constructor creates a query object and initializes the queryOrders used for sorting results
     *
     * @since 1.0.0
     *
     * @param entityType  Managed Entity Type
     * @param queryOrders list of queryOrders to order by multiple fields and directions
     *
     * <pre>
     * <code>
     *
     *   Query query = new Query(MyEntity.class, Arrays.asList(new QueryOrder("firstName")));
     *   List results = manager.executeQuery(query);
     *
     * </code>
     * </pre>
     * */
    @SuppressWarnings("unused")
    public Query(Class entityType, QueryOrder... queryOrders)
    {
        this.entityType = entityType;
        this.queryOrders = Arrays.asList(queryOrders);
    }

    /**
     * Constructor creates a query object and initializes the criteria used for filtering and the queryOrders used for sorting results
     *
     *
     * @since 1.0.0
     *
     * @param entityType  Managed Entity Type
     * @param criteria    criteria used to filter results
     * @param queryOrders list of queryOrders to order by multiple fields and directions
     *
     * <pre>
     * <code>
     *
     *   Query query = new Query(MyEntity.class, Arrays.asList(new QueryOrder("firstName")));
     *   List results = manager.executeQuery(query);
     *
     * </code>
     * </pre>
     * */
    @SuppressWarnings("unused")
    public Query(Class entityType, QueryCriteria criteria, QueryOrder... queryOrders)
    {
        this.entityType = entityType;
        this.criteria = criteria;
        this.queryOrders = Arrays.asList(queryOrders);
    }

    /**
     * Constructor creates a query object and initializes the criteria object used for filtering results along with a list of selection fields
     * @since 1.0.0
     * @param entityType Managed Entity Type
     * @param selections List of attributes to return in query results
     * @param criteria   Query filter criteria
     *
     * <code>
     *   Query query = new Query(MyEntity.class, Arrays.asList("name", "description"), new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
     *   List results = manager.executeQuery(query);
     * </code>
     */
    @SuppressWarnings("unused")
    public Query(Class entityType, List<String> selections, QueryCriteria criteria)
    {
        this.entityType = entityType;
        this.selections = selections;
        this.criteria = criteria;
    }

    /**
     * Constructor creates a query object and initializes the criteria object used for filtering results along with an array of update details. The array of sections fields are parameters 2..n
     * @since 1.0.0
     * @param entityType Managed Entity Type
     * @param criteria   Query filter criteria
     * @param updates    Array of attribute update instructions
     *
     * <code>
     *   Query query = new Query(Person.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"), new AttributeUpdate("name", "Jim");
     *   List results = manager.executeUpdate(query);
     * </code>
     */
    @SuppressWarnings("unused")
    public Query(Class entityType, QueryCriteria criteria, AttributeUpdate... updates)
    {
        this.entityType = entityType;
        this.updates = Arrays.asList(updates);
        this.criteria = criteria;
    }

    /**
     * creates a query object and initializes the criteria object used for filtering results along with a list of updates that can be used to update all rows returned from the query
     * @since 1.0.0
     *
     * <code>
     *   Query query = new Query(Person.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"), Arrays.asList(new AttributeUpdate("name", "Jim"));
     *   List results = manager.executeUpdate(query);
     * </code>
     *
     * @param entityType Entity Type
     * @param criteria   Query filter criteria
     * @param updates    List of attribute update instructions
     */
    @SuppressWarnings("unused")
    public Query(Class entityType, QueryCriteria criteria, List<AttributeUpdate> updates)
    {
        this.entityType = entityType;
        this.criteria = criteria;
        this.updates = updates;
    }

    /**
     * Constructor creates a query object and initializes the criteria object used for filtering results along with a list of selection fields and a list of queryOrders that can be used for sorting
     * @since 1.0.0
     * @param entityType  Entity Type
     * @param selections  List of attributes to return in query results
     * @param criteria    Query filter criteria
     * @param queryOrders Query Sort Order
     *
     * <code>
     *   Query query = new Query(Person.class,
     *                           Arrays.asList("name"),
     *                           new QueryCriteria("attributeName"),
     *                           QueryCriteriaOperator.EQUAL, "key"),
     *                           Arrays.asList(new QueryOrder("name", true));
     *   List results = manager.executeQuery(query);
     * </code>
     */
    @SuppressWarnings("unused")
    public Query(Class entityType, List<String> selections, QueryCriteria criteria, List<QueryOrder> queryOrders)
    {
        this.entityType = entityType;
        this.selections = selections;
        this.criteria = criteria;
        this.queryOrders = queryOrders;
    }

    /**
     * Constructor creates a query object and initializes the criteria object used for filtering results along with a list of selection fields and a list of queryOrders that can be used for sorting
     * @since 1.0.0
     * @param entityType Entity Type
     * @param selections List of attributes to return in query results
     * @param criteria   Query filter criteria
     *
     * <code>
     *   Query query = new Query(Person.class,
     *                           Arrays.asList("name"),
     *                           new QueryCriteria("attributeName"),
     *                           QueryCriteriaOperator.EQUAL, "key"),
     *                           Arrays.asList(new QueryOrder("name", true));
     *   List results = manager.executeQuery(query);
     * </code>
     */
    @SuppressWarnings("unused")
    public Query(Class entityType, QueryCriteria criteria, String... selections)
    {
        this.entityType = entityType;
        this.selections = Arrays.asList(selections);
        this.criteria = criteria;
    }

    /**
     * Get the entity type to query
     * @since 1.0.0
     * @return Entity Type
     */
    public Class getEntityType()
    {
        return entityType;
    }

    /**
     * Sets the base entity type to query upon
     * @since 1.0.0
     * @param entityType Entity Type
     */
    @SuppressWarnings("unused")
    public void setEntityType(Class entityType)
    {
        this.entityType = entityType;
    }

    /**
     * Gets the selection fields used to limit the result fields when doing a fetch
     * @since 1.0.0
     * @return List of Attribute Update specifications
     */
    public List<AttributeUpdate> getUpdates()
    {
        return updates;
    }

    /**
     * Sets the update instructions
     * @since 1.0.0
     * @param updates List of Attribute Updates
     */
    @SuppressWarnings("unused")
    public void setUpdates(List<AttributeUpdate> updates)
    {
        this.updates = updates;
    }

    /**
     * Gets the selection fields used to limit the result fields when doing a fetch
     * @since 1.0.0
     * @return List of attribute selection names
     */
    public List<String> getSelections()
    {
        return selections;
    }

    /**
     * Sets the selection fields to a list of strings that are used to limit the result fields when doing a fetch
     * @param selections List of attribute names
     */
    public void setSelections(List<String> selections)
    {
        this.selections = selections;
    }

    /**
     * Gets the criteria object that is used to filter when doing a fetch
     * @since 1.0.0
     * @return Query Filter Criteria
     */
    public QueryCriteria getCriteria()
    {
        return criteria;
    }

    /**
     * Sets the criteria object that is used to filter when doing a fetch
     * @since 1.0.0
     * @param criteria Query Filter Criteria
     */
    @SuppressWarnings("unused")
    public void setCriteria(QueryCriteria criteria)
    {
        this.criteria = criteria;
    }

    /**
     * Gets the queryOrder objects that are used to sort the result set
     * @since 1.0.0
     * @return List of Query sort order
     */
    public List<QueryOrder> getQueryOrders()
    {
        return queryOrders;
    }

    /**
     * Sets the queryOrder objects that are used to sort the result set
     * @since 1.0.0
     * @param queryOrders List of Query sort order
     */
    public void setQueryOrders(List<QueryOrder> queryOrders)
    {
        this.queryOrders = queryOrders;
    }

    /**
     * Gets the first row of records to return a subset of results
     * @since 1.0.0
     * @return First row
     */
    public int getFirstRow()
    {
        return firstRow;
    }

    /**
     * Sets the first row of records to return a subset of results
     * @since 1.0.0
     * @param firstRow First row
     */
    public void setFirstRow(int firstRow)
    {
        this.firstRow = firstRow;
    }

    /**
     * Gets the maximum number of results to return
     * @since 1.0.0
     * @return Max Results
     */
    public int getMaxResults()
    {
        return maxResults;
    }

    /**
     * Sets the maximum number of results to return
     * @since 1.0.0
     * @param maxResults Mx Results
     */
    public void setMaxResults(int maxResults)
    {
        this.maxResults = maxResults;
    }

    /**
     * Returns the result count of an executed query
     *
     * @since 1.0.0
     * @return Results count of executed query
     */
    @SuppressWarnings("unused")
    public int getResultsCount()
    {
        return resultsCount;
    }

    /**
     * Set the result count of a query after executing query
     *
     * @since 1.0.0
     * @param resultsCount Number of results in query results
     */
    public void setResultsCount(int resultsCount)
    {
        this.resultsCount = resultsCount;
    }

    /**
     * Terminate the query that is currently running
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public void terminate()
    {
        kill = true;
    }

    /**
     * The query has been terminated before completion
     * @since 1.0.0
     * @return Boolean flag to terminate query
     */
    public boolean isTerminated()
    {
        return kill;
    }

    /**
     * Get the partition key specified in the query
     *
     * @since 1.0.0
     * @return Partition Value
     */
    public Object getPartition()
    {
        return partition;
    }

    /**
     * Set the partition ID to query from
     *
     * @since 1.0.0
     * @param partition Partition Value
     */
    public void setPartition(Object partition)
    {
        this.partition = partition;
    }

    /**
     * Custom serialization to write object to a buffer
     *
     * @param buffer Offheap Buffer
     * @throws IOException Failure to serialize query
     */
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(selections);
        buffer.writeObject(updates);
        buffer.writeObject(criteria);
        buffer.writeObject(queryOrders);
        buffer.writeObject(entityType.getName());
        buffer.writeObject(partition);
        buffer.writeInt(firstRow);
        buffer.writeInt(maxResults);
        buffer.writeInt(resultsCount);
        buffer.writeObject(queryListener);
        buffer.writeBoolean(distinct);
    }

    /**
     * Custom serialization to read an object from a buffer
     *
     * @param buffer ObjectBuffer
     * @throws IOException failure to deserialize query
     */
    @Override
    @SuppressWarnings("unchecked")
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        selections = (List<String>) buffer.readObject();
        updates = (List<AttributeUpdate>) buffer.readObject();
        criteria = (QueryCriteria) buffer.readObject();
        queryOrders = (List<QueryOrder>) buffer.readObject();
        try
        {
            entityType = Class.forName((String) buffer.readObject());
        }
        catch (ClassNotFoundException e)
        {
            entityType = null;
        }
        partition = buffer.readObject();
        firstRow = buffer.readInt();
        maxResults = buffer.readInt();
        resultsCount = buffer.readInt();
        queryListener = (QueryListener)buffer.readObject();
        distinct = buffer.readBoolean();
    }

    private QueryListener queryListener;

    /**
     * This method will allow subscribers to query results.  If a record that matches the query critieria is either
     * added, updated, or removed.  The query listener will be invoked. Note, if you use this, do not forget to remove
     * the listeners when done.  If you fail to remove the listener that could degrade performance.
     *
     * This is compatable with the remote persistence manager, embedded persistence manager, and the in memory persistence manager
     *
     * Simple usage is
     *
     * <p>
     *             Query query = new Query(SystemEntity.class);
     *             query.setCriteria(new QueryCriteria("id", QueryCriteriaOperator.NOT_EQUAL, 2));
     *             query.setChangeListener(new QueryListener() {
     *                  @Override
     *                  public void onItemUpdated(IManagedEntity items) {
     *                      ...
     *                  }
     *
     *                  @Override
     *                  public void onItemAdded(IManagedEntity items) {
     *
     *                  }
     *
     *                  @Override
     *                  public void onItemRemoved(IManagedEntity items) {
     *
     *                  }
     *            });
     * </p>
     *
     *
     * @since 1.3.0
     *
     * @param queryListener Query Listener Delegate
     */
    @SuppressWarnings("EmptyMethod unused")
    public void setChangeListener(QueryListener queryListener)
    {
        this.queryListener = queryListener;
    }

    /**
     * Get Query Listener
     *
     * @return Query listener
     * @since 1.3.0
     */
    public QueryListener getChangeListener() {
        return queryListener;
    }

    /**
     * Read object with position
     *
     * @param buffer   ObjectBuffer
     * @param position Position within data file
     * @throws IOException Failure to deserialize object
     */
    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException
    {

    }

    @Override
    public int hashCode()
    {
        return Objects.hash(entityType, partition, queryOrders, criteria, selections, distinct);
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == this)
            return true;

        if(other instanceof Query) {
            Query otherQuery = (Query) other;
            return this.entityType.equals(otherQuery.entityType) && this.distinct == otherQuery.distinct && CompareUtil.forceCompare(this.partition, otherQuery.partition) && this.criteria.equals(otherQuery.criteria) && CompareUtil.forceCompare(this.queryOrders, otherQuery.queryOrders) && CompareUtil.forceCompare(this.selections, otherQuery.selections);
        }
        return false;
    }

    private transient Set<QueryCriteria> allCriteria = null;

    /**
     * Getter for all criteria.  This is stored within an unordered list.
     * It was added so the scanners can check the entire list of critieria
     * rather than walking through the root level.
     *
     * @return Unordered set of Query Critieria
     *
     * @since 1
     */
    public Set<QueryCriteria> getAllCriteria()
    {
        if(allCriteria == null
                || allCriteria.size() == 0)
        {
            synchronized (this)
            {
                if(allCriteria == null || allCriteria.size() == 0)
                {
                    allCriteria = new HashSet<>();
                    aggregateCritieria(this.criteria, allCriteria);
                }
            }
        }

        return allCriteria;
    }

    /**
     * This method aggregates the list of criteria and sub criteria into
     * a single list so that it does not have to be done upon checking criteria
     * each iteration
     *
     * @param criteria Root Criteria
     * @param allCritieria Maintained list of all criteria
     * @return List of all criteria
     *
     * @since 1.3.0 Re-vamped criteria checking to address bugs and maintain
     *              record insertion criteria checking
     */
    @SuppressWarnings("UnusedReturnValue")
    private static Set<QueryCriteria> aggregateCritieria(QueryCriteria criteria, Set<QueryCriteria> allCritieria)
    {
        if(criteria == null)
            return allCritieria;

        allCritieria.add(criteria);

        for (QueryCriteria subCriteria : criteria.getSubCriteria())
        {
            aggregateCritieria(subCriteria, allCritieria);
            subCriteria.setParentCriteria(criteria);

            // This indicates it is a root criteria.  In that case, we need to
            // look at the first sub criteria and assign its modifier
            if(!criteria.isAnd() && !criteria.isOr())
            {
                if(subCriteria.isOr())
                    criteria.setOr(true);
                else
                    criteria.setAnd(true);
            }
        }

        return allCritieria;
    }

    /**
     * Whether to select unique row results.  This is default to true.  This does not have
     * an effect on entity queries since entity queries are distinct by default.
     *
     * @since 1.3.1 - Feature added to apply distinct rows for queries that define selections
     * @param distinct Boolean value true or false
     */
    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    /**
     * Getter for distinct property
     *
     * @since 1.3.1 New Feature added
     * @return Whether to select unique row results.
     */
    public boolean isDistinct()
    {
        return distinct;
    }

    /**
     * This method is used to optimize the criteria.  If an identifier is included, that will move that
     * criteria to the top.  Next if an index is included, that will be moved to the top.
     * <p>
     * This was added as an enhancement so that the query is self optimized
     *
     * @param descriptor Entity descriptor to get entity information regarding indexed, relationship, and identifier fields
     * @since 1.3.0 An effort to cleanup query results in preparation for query caching.
     */
    public void sortCritieria(EntityDescriptor descriptor) {
        if(criteria != null && descriptor != null) {
            Collections.sort(criteria.getSubCriteria(), (o1, o2) -> {

                // Check identifiers first
                boolean o1isIdentifier = descriptor.getIdentifier().getName().equals(o1.getAttribute());
                boolean o2isIdentifier = descriptor.getIdentifier().getName().equals(o2.getAttribute());

                if (o1isIdentifier && !o2isIdentifier)
                    return 1;
                else if (o2isIdentifier && !o1isIdentifier)
                    return -1;

                // Check indexes next
                boolean o1isIndex = descriptor.getIndexes().get(o1.getAttribute()) != null;
                boolean o2isIndex = descriptor.getIndexes().get(o2.getAttribute()) != null;

                if (o1isIndex && !o2isIndex)
                    return 1;
                else if (o2isIndex && !o1isIndex)
                    return -1;

                // Check relationships last.  A full table scan is prefered before a relationship
                boolean o1isRelationship = descriptor.getRelationships().get(o1.getAttribute()) != null;
                boolean o2isRelationship = descriptor.getRelationships().get(o2.getAttribute()) != null;

                if (o1isRelationship && !o2isRelationship)
                    return -1;
                else if (o2isRelationship && !o1isRelationship)
                    return 1;

                if (o1.getOperator().isIndexed() && !o2.getOperator().isIndexed())
                    return 1;
                else if (o2.getOperator().isIndexed() && !o1.getOperator().isIndexed())
                    return -1;

                // Lastly check for operators.  EQUAL has priority since it is less granular
                if (o1.getOperator() == QueryCriteriaOperator.EQUAL
                        && o2.getOperator() == QueryCriteriaOperator.EQUAL)
                    return 0;
                else if (o1.getOperator() == QueryCriteriaOperator.EQUAL)
                    return 1;
                else if (o2.getOperator() == QueryCriteriaOperator.EQUAL)
                    return -1;
                else
                    return 0;
            });
        }
    }
}
