package com.onyx.persistence.query;

import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;

import java.io.IOException;
import java.io.Serializable;


/**
 * The purpose of this is to specify the query sort order while querying.
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *
 *   PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 *   Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
 *   query.setQueryOrders(new QueryOrder("name", true)
 *
 *   List results = manager.executeQuery(query);
 *
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.query.Query
 * @see PersistenceManager#executeQuery(Query)
 */
public class QueryOrder implements ObjectSerializable, Serializable
{
    /**
     * Default Constructor
     * @since 1.0.0
     */
    public QueryOrder()
    {

    }

    boolean ascending = true;

    String attribute = "";

    /**
     * Constructor with default ascending order
     *
     * @since 1.0.0
     * @param attribute Entity attribute name
     */
    public QueryOrder(String attribute)
    {
        this.attribute = attribute;
    }

    /**
     * Constructor with attribute and ascending/descending option
     *
     * @since 1.0.0
     * @param attribute Entity attribute name
     * @param ascending Flag for specifying ascending or descending
     */
    public QueryOrder(String attribute, boolean ascending)
    {
        this.ascending = ascending;
        this.attribute = attribute;
    }

    /**
     * Flag for ascending order
     * @since 1.0.0
     * @return True for ascending.  False for descending
     */
    public boolean isAscending()
    {
        return ascending;
    }

    /**
     * Attribute name to sort on
     * @since 1.0.0
     * @return Attribute name
     */
    public String getAttribute()
    {
        return attribute;
    }


    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(attribute);
        buffer.writeBoolean(ascending);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        attribute = (String)buffer.readObject();
        ascending = buffer.readBoolean();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }
}
