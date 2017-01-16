package com.onyx.persistence.query;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.update.AttributeUpdate;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

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
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Query implements ObjectSerializable, Serializable
{

    protected List<String> selections;
    protected List<AttributeUpdate> updates;
    protected QueryCriteria criteria;
    protected List<QueryOrder> queryOrders;

    protected Class entityType;
    protected int firstRow = 0;
    protected int maxResults = -1;
    protected int resultsCount;

    /**
     * Stop query before it finishes
     * @since 1.0.0
     */
    @JsonIgnore
    protected volatile boolean kill;

    protected Object partition = "";

    /**
     * Constructor creates an empty Query object
     * @since 1.0.0
     */
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
    public Query(Class entityType, QueryCriteria criteria, QueryOrder queryOrder)
    {
        this.entityType = entityType;
        this.criteria = criteria;
        this.queryOrders = Arrays.asList(queryOrder);
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
    public Query(Class entityType, QueryOrder queryOrder)
    {
        this.entityType = entityType;
        this.queryOrders = Arrays.asList(queryOrder);
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
    @JsonIgnore
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
    }

    /**
     * Custom serialization to read an object from a buffer
     *
     * @param buffer ObjectBuffer
     * @throws IOException failure to deserialize query
     */
    @Override
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
}
