package com.onyx.persistence

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.context.SchemaContext
import com.onyx.diskmap.serializer.ObjectBuffer
import com.onyx.diskmap.serializer.ObjectSerializable
import com.onyx.extension.common.catchAll
import com.onyx.extension.descriptor
import com.onyx.extension.get
import com.onyx.extension.set

import java.io.IOException

/**
 * All managed entities should extend this class
 * Base class is needed for proper serialization
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.IManagedEntity
 *
 * @since 1.0.0
 */
abstract class ManagedEntity : IManagedEntity, ObjectSerializable {

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
            descriptor = descriptor(context)
        }
        return descriptor!!
    }

    /**
     * Write this entity to an object buffer
     * @param buffer Object buffer to write to
     */
    @Throws(IOException::class)
    override fun writeObject(buffer: ObjectBuffer) {
        val descriptor = getDescriptor(buffer.serializers.context)

        descriptor.attributes.values.forEach {
            catchAll {
                buffer.writeObject(this[buffer.serializers.context, descriptor, it.name])
            }
        }
    }

    /**
     * Read attributes from buffer
     */
    override fun readObject(buffer: ObjectBuffer) = readObject(buffer, 0L, 0)

    /**
     * Read attributes from buffer with a specific serializer id
     *
     * @param buffer Object buffer to read
     * @param position Ignored
     * @param serializerId Version of managed entity
     */
    @Throws(IOException::class)
    override fun readObject(buffer: ObjectBuffer, position: Long, serializerId: Int) {

        val descriptor = getDescriptor(buffer.serializers.context)

        // If System Entity does not exist, read by entity descriptor
        if (serializerId == 0) {
            descriptor.attributes.values.forEach {
                catchAll {
                    this[buffer.serializers.context, descriptor, it.name] = buffer.readObject()
                }
            }
        } else {
            val systemEntity = buffer.serializers.context.getSystemEntityById(serializerId)
            systemEntity!!.attributes.forEach {
                catchAll {
                    this[buffer.serializers.context, descriptor, it.name] = buffer.readObject()
                }
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
                    this[context, descriptor, it.name] = attributeValueWithinMap
                }
            }
        }
    }
}
