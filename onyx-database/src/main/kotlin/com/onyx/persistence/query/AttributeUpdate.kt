package com.onyx.persistence.query

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.AttributeDescriptor
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.manager.PersistenceManager

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
class AttributeUpdate @JvmOverloads constructor(var fieldName: String? = null, var value: Any? = null): BufferStreamable {

    @Transient
    var attributeDescriptor: AttributeDescriptor? = null
    @Transient
    var indexInteractor: IndexInteractor? = null

    override fun write(buffer: BufferStream) {
        buffer.putObject(fieldName)
        buffer.putObject(value)
    }

    override fun read(buffer: BufferStream) {
        fieldName = buffer.`object` as String
        value = buffer.`object`
    }
}
