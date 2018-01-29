package entities

import com.onyx.buffer.BufferStream
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType
import com.onyx.persistence.context.SchemaContext

import java.util.Date

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
class PerformanceEntity : AbstractEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute
    var longValue: Long? = null
    @Attribute
    var longPrimitive: Long = 0
    @Attribute
    var intValue: Int? = null
    @Attribute
    var intPrimitive: Int = 0
    @Attribute
    var stringValue: String? = null
    @Attribute
    var dateValue: Date? = null
    @Attribute
    var doubleValue: Double? = null
    @Attribute
    var doublePrimitive: Double = 0.toDouble()
    @Attribute
    var booleanValue: Boolean? = null
    @Attribute
    var booleanPrimitive: Boolean = false

    @Attribute
    @Index
    var idValue: Long = 0

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = PerformanceEntityChild::class, inverse = "parent", cascadePolicy = CascadePolicy.ALL)
    var child: PerformanceEntityChild? = null

    override fun write(buffer: BufferStream, context: SchemaContext?) {

        val systemEntity = context!!.getSystemEntityByName(this::class.java.name)
        buffer.putInt(systemEntity!!.primaryKey)

        buffer.putObject(id)
        buffer.putObject(longValue)
        buffer.putLong(longPrimitive)
        buffer.putObject(intValue)
        buffer.putInt(intPrimitive)
        buffer.putObject(stringValue)
        buffer.putObject(dateValue)
        buffer.putObject(doubleValue)
        buffer.putDouble(doublePrimitive)
        buffer.putObject(booleanValue)
        buffer.putBoolean(booleanPrimitive)
        buffer.putLong(idValue)
        buffer.putObject(dateCreated)
        buffer.putObject(dateUpdated)
        buffer.putDouble(doubleSample)
        buffer.putObject(dblSample)
    }

    override fun read(buffer: BufferStream, context: SchemaContext?) {

        val serializerId = buffer.int

        val currentSystemEntity = context?.getSystemEntityByName(this::class.java.name)
        if(currentSystemEntity?.primaryKey != serializerId) {
            buffer.byteBuffer.position(buffer.byteBuffer.position() - Integer.BYTES)
            super.read(buffer, context)
        } else {
            id = buffer.getObject(context) as Long?
            longValue = buffer.getObject(context) as Long?
            longPrimitive = buffer.long
            intValue = buffer.getObject(context) as Int?
            intPrimitive = buffer.int
            stringValue = buffer.getObject(context) as? String
            dateValue = buffer.getObject(context) as? Date
            doubleValue = buffer.getObject(context) as? Double
            doublePrimitive = buffer.double
            booleanValue = buffer.getObject(context) as? Boolean
            booleanPrimitive = buffer.boolean
            idValue = buffer.long
            dateCreated = buffer.getObject(context) as Date?
            dateUpdated = buffer.getObject(context) as Date?
            doubleSample = buffer.double
            dblSample = buffer.getObject(context) as Double?
        }
    }
}
