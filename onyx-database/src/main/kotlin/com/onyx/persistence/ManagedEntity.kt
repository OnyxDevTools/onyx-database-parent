package com.onyx.persistence

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.context.SchemaContext
import com.onyx.extension.common.catchAll
import com.onyx.extension.get
import com.onyx.extension.set
import com.onyx.persistence.context.Contexts

/**
 * All managed entities should extend this class
 * Base class is needed for proper serialization
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.IManagedEntity
 *
 * @since 1.0.0
 */
abstract class ManagedEntity : IManagedEntity, BufferStreamable {

    @Transient private var descriptor: EntityDescriptor? = null
    @Transient internal var ignoreListeners = false

    /**
     * Get the corresponding descriptor for this entity given its context
     *
     * @param context Owning schema context
     * @since 2.0.0
     */
    private fun getDescriptor(context: SchemaContext):EntityDescriptor {
        if (descriptor == null) {
            descriptor = context.getDescriptorForEntity(this, "")
        }
        return descriptor!!
    }

    override fun write(buffer: BufferStream) {
        val descriptor = getDescriptor(Contexts.first())
        descriptor.reflectionFields.forEach { name, _ ->
            buffer.putOther(this.get(descriptor = descriptor, name = name))
        }
    }

    override fun read(buffer: BufferStream) {
        val descriptor = getDescriptor(Contexts.first())
        descriptor.reflectionFields.forEach { name, _ ->
            this.set(descriptor = descriptor, name = name, value = buffer.other)
        }
    }

    override fun write(buffer: BufferStream, context: SchemaContext?) {

        val systemEntity = context!!.getSystemEntityByName(this::class.java.name)
        buffer.putInt(systemEntity!!.primaryKey)

        systemEntity.attributes.forEach {
            catchAll {
                buffer.putObject(this.get(context = context, name = it.name))
            }
        }
    }

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        val serializerId = buffer.int
        val systemEntity = context!!.getSystemEntityById(serializerId)
        systemEntity!!.attributes.forEach {
            catchAll {
                this.set(context = context, name = it.name, value = buffer.value)
            }
        }
    }

    /**
     * This method maps the keys from a structure to the attributes of the entity
     * @param mapObj Map to convert from
     */
    fun fromMap(mapObj: Map<String, Any>, context: SchemaContext) {
        val descriptor = getDescriptor(context)

        descriptor.attributes.values.forEach {
            catchAll {
                if (mapObj.containsKey(it.name)) {
                    val attributeValueWithinMap = mapObj[it.name]
                    this.set(context = context, descriptor = descriptor, name = it.name, value = attributeValueWithinMap)
                }
            }
        }
    }
}
