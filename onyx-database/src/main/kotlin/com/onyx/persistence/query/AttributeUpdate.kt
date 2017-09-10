package com.onyx.persistence.query

import com.onyx.descriptor.AttributeDescriptor
import com.onyx.index.IndexController
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.diskmap.serializer.ObjectBuffer
import com.onyx.diskmap.serializer.ObjectSerializable

import java.io.IOException

/**
 * Used to specify what attributes to update.
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * Query query = new Query(MyEntity.class, new QueryCriteria("attributeName", QueryCriteriaOperator.EQUAL, "key"));
 * query.setUpdates(new AttributeUpdate("name", "Bob");
 * query.setQueryOrders(new QueryOrder("name", true)
 *
 * List results = manager.executeUpdate(query);
 *
 * @see com.onyx.persistence.query.Query
 *
 * @see PersistenceManager.executeQuery
 */
@Suppress("UNCHECKED_CAST")
class AttributeUpdate @JvmOverloads constructor(var fieldName: String? = null, var value: Any? = null): ObjectSerializable {

    @Transient
    var attributeDescriptor: AttributeDescriptor? = null
    @Transient
    var indexController: IndexController? = null

    @Throws(IOException::class)
    override fun writeObject(buffer: ObjectBuffer) {
        buffer.writeObject(fieldName)
        buffer.writeObject(value)
    }

    @Throws(IOException::class)
    override fun readObject(buffer: ObjectBuffer) {
        fieldName = buffer.readObject() as String
        value = buffer.readObject()
    }
}
