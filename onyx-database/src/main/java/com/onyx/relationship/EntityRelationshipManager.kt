package com.onyx.relationship

import com.onyx.exception.AttributeMissingException
import com.onyx.extension.identifier
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Created by timothy.osborn on 12/23/14.
 *
 * The purpose of this class is to retain a reference to all of the elements that you are transacting
 *
 * There are two usages for this.
 *
 * 1) When saving you can fetch the same reference for an inverse relationship and apply it
 * 2) When fetching recursive elements you do not have to re-fetch an element you have already fetched
 */
class EntityRelationshipManager {
    private val entities = HashMap<String, MutableMap<Any, IManagedEntity>>()

    /**
     *
     * Checks to see whether it exists in the entities structure
     * @param entity Entity to check to see if action has already been taken
     * @param context Entity's context
     * @throws AttributeMissingException Attribute does not exist
     * @return Whether the entity is already there
     */
    @Throws(AttributeMissingException::class)
    fun contains(entity: IManagedEntity, context: SchemaContext): Boolean = entities[entity.javaClass.name]?.contains(entity.identifier(context)) ?: false

    /**
     * Adds a new key to the 2 dimensional structure
     *
     * @param entity Entity to add to controller
     * @param context Entity's context
     * @throws AttributeMissingException Attribute does not exist
     */
    @Throws(AttributeMissingException::class)
    fun add(entity: IManagedEntity, context: SchemaContext) { entities.getOrPut(entity.javaClass.name){ HashMap()}[entity.identifier(context)!!] = entity }

    /**
     * Gets the element, from the 2 dimensional structure
     *
     * @param entity Entity to check
     * @param context Entity's context
     * @return Entity reference
     */
    @Throws(AttributeMissingException::class)
    operator fun get(entity: IManagedEntity, context: SchemaContext): IManagedEntity? = entities[entity.javaClass.name]!![entity.identifier(context)]
}
