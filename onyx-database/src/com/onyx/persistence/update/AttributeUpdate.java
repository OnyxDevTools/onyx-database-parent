package com.onyx.persistence.update;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.index.IndexController;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Used to specify what attributes to update.
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *
 *   PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 *   Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
 *   query.setUpdates(new AttributeUpdate("name", "Bob");
 *   query.setQueryOrders(new QueryOrder("name", true)
 *
 *   List results = manager.executeUpdate(query);
 *
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.query.Query
 * @see PersistenceManager#executeQuery(Query)
 */
public class AttributeUpdate<T>  implements ObjectSerializable, Externalizable
{

    /**
     * Default Constructor
     */
    public AttributeUpdate()
    {

    }

    protected String fieldName;
    protected T value;
    transient protected AttributeDescriptor attributeDescriptor;
    transient protected IndexController indexController;

    /**
     * Creates a new IntegerUpdate Instruction object
     * @param fieldName Attribute Name
     * @param value Value to update to
     */
    public AttributeUpdate(String fieldName, T value) {
        this.fieldName = fieldName;
        this.value = value;
    }

    /**
     * Gets the Value to update to
     * @return Value to update to
     */
    public Object getValue()
    {
        return value;
    }

    /**
     * Set Value to update to
     * @param value Value to update to
     */
    public void setValue(Object value)
    {
        this.value = (T)value;
    }

    /**
     * Gets the field name to update
     * @return Attribute Name
     */
    public String getFieldName()
    {
        return fieldName;
    }

    public AttributeDescriptor getAttributeDescriptor()
    {
        return attributeDescriptor;
    }

    public void setAttributeDescriptor(AttributeDescriptor attributeDescriptor)
    {
        this.attributeDescriptor = attributeDescriptor;
    }

    public IndexController getIndexController()
    {
        return indexController;
    }

    public void setIndexController(IndexController indexController)
    {
        this.indexController = indexController;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(fieldName);
        buffer.writeObject(value);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        fieldName = (String)buffer.readObject();
        value = (T)buffer.readObject();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(value);
        out.writeUTF(fieldName);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        value = (T)in.readObject();
        fieldName = in.readUTF();
    }
}
