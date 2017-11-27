package com.onyx.extension

import com.onyx.exception.AttributeNonNullException
import com.onyx.exception.AttributeSizeException
import com.onyx.exception.IdentifierRequiredException
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.context.SchemaContext

/**
 * Checks an entity to to see if it is valid within a context
 *
 * @param context Context to verify entity against
 *
 * @throws OnyxException The entity is invalid
 * @return true if it is valid
 * @since 2.0.0
 */
@Throws(OnyxException::class)
fun IManagedEntity.isValid(context:SchemaContext):Boolean {
    val descriptor = descriptor(context)

    descriptor.attributes.values.forEach {
        val attributeValue:Any? = this[context, descriptor, it.name]

        // Nullable
        if(!it.isNullable && attributeValue == null) throw AttributeNonNullException(AttributeNonNullException.ATTRIBUTE_NULL_EXCEPTION, it.name)

        // Size
        if(it.type.isAssignableFrom(String::class.java) && attributeValue != null && (attributeValue as String).length > it.size && it.size > -1)
            throw AttributeSizeException(AttributeSizeException.ATTRIBUTE_SIZE_EXCEPTION, it.name)
    }

    // Null Identifier if not auto generated
    if(descriptor.identifier!!.generator === IdentifierGenerator.NONE){
        if(identifier(context) == null) throw IdentifierRequiredException(IdentifierRequiredException.IDENTIFIER_REQUIRED_EXCEPTION, descriptor.identifier!!.name)
    }

    return true
}