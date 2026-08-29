package entities

import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Vector fixture that deliberately keeps JVM primitive and boxed forms side by side.
 *
 * Non-null Kotlin scalar properties compile to primitive fields; nullable properties compile to
 * their boxed counterparts. Keeping both on one entity catches reflection/type-routing drift
 * between record encoding and predicate planning.
 */
@Entity(fileName = "vector-primitive-boxed/", entropy = 64)
class VectorPrimitiveBoxedEntity : VectorManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var id: Long = 0

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveByte: Byte = 0

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedByte: Byte? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveShort: Short = 0

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedShort: Short? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveInt: Int = 0

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedInt: Int? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveLong: Long = 0L

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedLong: Long? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveFloat: Float = 0.0f

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedFloat: Float? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveDouble: Double = 0.0

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedDouble: Double? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveBoolean: Boolean = false

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedBoolean: Boolean? = null

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var primitiveChar: Char = '\u0000'

    @Attribute
    @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
    var boxedChar: Char? = null
}
