package com.onyx.validators

import com.onyx.descriptor.AttributeDescriptor
import com.onyx.descriptor.IdentifierDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.*
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.IdentifierGenerator
import com.onyx.persistence.annotations.RelationshipType

/**
 * Created by tosborn1 on 9/7/17.
 *
 * This class is responsible for validating an entity has all the proper properties and annotations
 */
object EntityValidation {

    /**
     * Verify The specified class inherits from Managed Entity
     *
     * @param entityClass Class to reflect
     */
    @Throws(EntityClassNotFoundException::class)
    fun validateIsManagedEntity(entityClass:Class<*>) {
        if (!ManagedEntity::class.java.isAssignableFrom(entityClass)) {
            throw EntityClassNotFoundException(EntityClassNotFoundException.EXTENSION_NOT_FOUND, entityClass)
        }

        // This class does not have the entity annotation.  This is required
        if (entityClass.getAnnotation(Entity::class.java) == null) {
            throw EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND, entityClass)
        }
    }

    /**
     * Validate Relationships.
     *
     * @throws EntityClassNotFoundException     when class is not found
     * @throws InvalidRelationshipTypeException when relationship is not valid
     */
    @Throws(EntityClassNotFoundException::class, InvalidRelationshipTypeException::class)
    fun validateRelationships(entityClass:Class<*>, relationships:Map<String, RelationshipDescriptor>) {
        for ((inverse, parentClass, relationshipType, inverseClass, _, _, _, name, type) in relationships.values) {

            if (!ManagedEntity::class.java.isAssignableFrom(entityClass)) {
                throw EntityClassNotFoundException(EntityClassNotFoundException.RELATIONSHIP_ENTITY_BASE_NOT_FOUND + ": " + inverseClass.name, entityClass)
            }

            if (type != inverseClass && (relationshipType == RelationshipType.MANY_TO_ONE || relationshipType == RelationshipType.ONE_TO_ONE)) {
                throw InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH)
            }

            if (inverseClass.getAnnotation(Entity::class.java) == null && (relationshipType == RelationshipType.MANY_TO_ONE || relationshipType == RelationshipType.ONE_TO_ONE)) {
                throw EntityClassNotFoundException(EntityClassNotFoundException.RELATIONSHIP_ENTITY_NOT_FOUND + ": " + inverseClass.name, entityClass)
            }

            if (inverse != null && inverse.isNotEmpty() &&
                    (relationshipType == RelationshipType.MANY_TO_ONE || relationshipType == RelationshipType.ONE_TO_ONE)) {

                try {
                    val inverseField = inverseClass.getDeclaredField(inverse)

                    if (relationshipType == RelationshipType.MANY_TO_ONE && inverseField.type != List::class.java) {
                        throw InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH)
                    } else if (relationshipType == RelationshipType.ONE_TO_ONE && inverseField.type != parentClass) {
                        throw InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_MISMATCH)
                    }
                } catch (e: NoSuchFieldException) {
                    throw InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_INVALID + " on " + inverseClass, e)
                }

            }

            if (relationshipType == RelationshipType.MANY_TO_MANY || relationshipType == RelationshipType.ONE_TO_MANY) {

                try {
                    val attributeField = entityClass.getDeclaredField(name)
                    var listFound = false

                    if (attributeField.type == List::class.java) {
                        listFound = true
                    }

                    attributeField.type.interfaces
                            .filter { it == List::class.java }
                            .forEach { listFound = true }

                    if (!listFound) {
                        throw InvalidRelationshipTypeException(EntityClassNotFoundException.TO_MANY_INVALID_TYPE)
                    }
                } catch (e: NoSuchFieldException) {
                    throw InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_INVALID, e)
                }

            }
        }
    }

    /**
     * Validate Indexes.
     *
     * @throws InvalidIndexException Index is not valid
     */
    @Throws(InvalidIndexException::class)
    fun validateIndexes(entityClass: Class<*>, indexes:Map<String, IndexDescriptor>) {
        indexes.values.forEach {
            try {
                entityClass.getDeclaredField(it.name)
            } catch (e: NoSuchFieldException) {
                throw InvalidIndexException(InvalidIndexException.INDEX_MISSING_FIELD)
            }
        }
    }

    /**
     * Validate Attributes.
     *
     * @throws EntityTypeMatchException Entity is not a valid type
     */
    @Throws(EntityTypeMatchException::class)
    fun validateAttributes(attributes:Map<String, AttributeDescriptor>) {
        attributes.values.forEach {

            val type = it.type

            if (!(Long::class.java.isAssignableFrom(type) ||
                    java.lang.Long::class.java.isAssignableFrom(type) ||
                    java.lang.Integer::class.java.isAssignableFrom(type) ||
                    java.lang.String::class.java.isAssignableFrom(type) ||
                    java.lang.Double::class.java.isAssignableFrom(type) ||
                    java.lang.Float::class.java.isAssignableFrom(type) ||
                    java.lang.Boolean::class.java.isAssignableFrom(type) ||
                    java.lang.Byte::class.java.isAssignableFrom(type) ||
                    java.util.Date::class.java.isAssignableFrom(type) ||
                    java.lang.Short::class.java.isAssignableFrom(type) ||
                    java.lang.Character::class.java.isAssignableFrom(type) ||
                    java.lang.Short::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    java.lang.Long::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    java.lang.Integer::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    java.lang.Double::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    java.lang.Float::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    java.lang.Boolean::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    java.lang.Byte::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    java.lang.Character::class.javaPrimitiveType!!.isAssignableFrom(type) ||
                    ByteArray::class.java.isAssignableFrom(type) ||
                    IntArray::class.java.isAssignableFrom(type) ||
                    LongArray::class.java.isAssignableFrom(type) ||
                    FloatArray::class.java.isAssignableFrom(type) ||
                    DoubleArray::class.java.isAssignableFrom(type) ||
                    BooleanArray::class.java.isAssignableFrom(type) ||
                    CharArray::class.java.isAssignableFrom(type) ||
                    ShortArray::class.java.isAssignableFrom(type) ||
                    Array<Char>::class.java.isAssignableFrom(type) ||
                    Array<Short>::class.java.isAssignableFrom(type) ||
                    Array<Byte>::class.java.isAssignableFrom(type) ||
                    Array<Int>::class.java.isAssignableFrom(type) ||
                    Array<Long>::class.java.isAssignableFrom(type) ||
                    Array<Float>::class.java.isAssignableFrom(type) ||
                    Array<Double>::class.java.isAssignableFrom(type) ||
                    Array<String>::class.java.isAssignableFrom(type) ||
                    Array<Boolean>::class.java.isAssignableFrom(type) ||
                    IManagedEntity::class.java.isAssignableFrom(type) ||
                    List::class.java.isAssignableFrom(type) ||
                    Set::class.java.isAssignableFrom(type) ||
                    type.isEnum)) {
                throw EntityTypeMatchException(EntityTypeMatchException.ATTRIBUTE_TYPE_IS_NOT_SUPPORTED + ": " + type)
            }
        }
    }

    /**
     * Validate Identifier.
     *
     * @throws InvalidIdentifierException Identifier is not valid
     */
    @Throws(InvalidIdentifierException::class)
    fun validateIdentifier(entityClass:Class<*>, identifier: IdentifierDescriptor?) {

        if(identifier == null) {
            throw InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING)
        }

        try {
            val idField = entityClass.getDeclaredField(identifier.name)

            if (!(idField.type == java.lang.Integer.TYPE
                    || idField.type == java.lang.Long.TYPE
                    || idField.type == java.lang.Double.TYPE
                    || idField.type == java.lang.Float.TYPE
                    || idField.type == java.lang.Short.TYPE
                    || idField.type == java.lang.Byte.TYPE
                    || idField.type == java.lang.Integer::class.java
                    || idField.type == java.lang.Long::class.java
                    || idField.type == java.lang.Double::class.java
                    || idField.type == java.lang.Float::class.java
                    || idField.type == java.lang.Short::class.java
                    || idField.type == java.lang.Byte::class.java
                    ) && identifier.generator == IdentifierGenerator.SEQUENCE) {
                throw InvalidIdentifierException(InvalidIdentifierException.INVALID_GENERATOR)
            }
        } catch (e: NoSuchFieldException) {
            throw InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_TYPE)
        } catch (e:UninitializedPropertyAccessException) {
            throw InvalidIdentifierException(InvalidIdentifierException.IDENTIFIER_MISSING)
        }
    }

}