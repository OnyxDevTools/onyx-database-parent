package com.onyx.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.onyx.exception.EntityClassNotFoundException
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.extension.common.*
import com.onyx.interactors.classfinder.ApplicationClassFinder
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.request.pojo.*

import java.io.IOException

/**
 * Created by timothy.osborn on 4/8/15.
 *
 * This class handles JSON serialization for a persistence web service
 */
class WebPersistenceEndpoint(private val persistenceManager: PersistenceManager, private val objectMapper: ObjectMapper, private val context: SchemaContext) {

    /**
     * Save Entity
     *
     * @param request Entity Request Body
     * @return Managed entity after save with populated id
     */
    fun save(request: EntityRequestBody): IManagedEntity {
        val clazz = ApplicationClassFinder.forName(request.type!!, context)
        val entity = objectMapper.convertValue(request.entity, clazz)
        persistenceManager.saveEntity(entity as IManagedEntity)

        return entity
    }

    /**
     * Find Entity
     *
     * @param request Entity Request Body
     * @return Hydrated entity
     */
    operator fun get(request: EntityRequestBody): IManagedEntity {
        val clazz = ApplicationClassFinder.forName(request.type, context)
        var entity: IManagedEntity

        entity = if (request.entity == null)
            clazz.instance()
        else
            objectMapper.convertValue(request.entity, clazz) as IManagedEntity

        if (request.id != null) {
            val descriptor = entity.descriptor(context)
            entity[context, descriptor, descriptor.identifier!!.name] = request.id.castTo(descriptor.identifier!!.type)
        }

        entity = persistenceManager.find(entity)
        return entity
    }

    /**
     * Find an entity by id and partition value
     */
    fun find(request: EntityFindRequest): IManagedEntity? {
        val clazz = ApplicationClassFinder.forName(request.type, context)
        return persistenceManager.findByIdInPartition(clazz, request.id!!, request.partitionValue ?: "")
    }

    /**
     * Delete Entity
     *
     * @param request Entity Request Body
     * @return Whether the entity was deleted or not
     */
    fun delete(request: EntityRequestBody): Boolean {
        val clazz = ApplicationClassFinder.forName(request.type, context)
        val entity: IManagedEntity
        entity = objectMapper.convertValue(request.entity, clazz) as IManagedEntity

        return persistenceManager.deleteEntity(entity)
    }

    /**
     * Execute Query
     *
     * @param query Query Body
     * @return Query Result body
     */
    fun executeQuery(query: Query): QueryResultResponseBody {
        val results = persistenceManager.executeQuery<Any>(query)
        return QueryResultResponseBody(query.resultsCount, results.toMutableList())
    }

    /**
     * Initialize Relationship.  Called upon a to many relationship
     *
     * @param request Initialize Body Error
     * @return List of relationship objects
     */
    fun initialize(request: EntityInitializeBody): Any? {
        val clazz = ApplicationClassFinder.forName(request.entityType, context)
        val entity = clazz.getDeclaredConstructor().newInstance() as IManagedEntity

        if (request.entityId != null) {
            val descriptor = entity.descriptor(context)
            entity[context, descriptor, descriptor.identifier!!.name] = request.entityId.castTo(descriptor.identifier!!.type)
        }

        if (request.partitionId != null && request.partitionId != "") {
            val descriptor = entity.descriptor(context)
            entity[context, descriptor, descriptor.partition!!.field.name] = request.partitionId.castTo(descriptor.partition!!.field.type)
        }


        persistenceManager.initialize(entity, request.attribute!!)
        return entity.getObject(context.getDescriptorForEntity(entity).relationships[request.attribute!!]!!.field)
    }

    /**
     * Batch Save
     *
     * @param request List of entity body
     */
    fun saveEntities(request: EntityListRequestBody) {
        val clazz = ApplicationClassFinder.forName(request.type, context)

        val javaType = objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
        val entities: List<IManagedEntity>

        entities = try {
            objectMapper.convertValue(request.entities, javaType)
        } catch (e: IOException) {
            throw EntityClassNotFoundException(OnyxException.UNKNOWN_EXCEPTION)
        }

        persistenceManager.saveEntities(entities)
    }

    /**
     * Batch Delete
     *
     * @param request Entity List body
     */
    fun deleteEntities(request: EntityListRequestBody) {
        val clazz = ApplicationClassFinder.forName(request.type, context)

        val javaType = objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
        val entities: List<IManagedEntity>

        entities = try {
            objectMapper.convertValue(request.entities, javaType)
        } catch (e: IOException) {
            throw EntityClassNotFoundException(OnyxException.UNKNOWN_EXCEPTION)
        }

        persistenceManager.deleteEntities(entities)
    }

    /**
     * Execute Update
     *
     * @param query Entity Query Body
     * @return Query Result body
     */
    fun executeUpdate(query: Query): Int =  persistenceManager.executeUpdate(query)

    /**
     * Execute Delete
     *
     * @param query Entity Query Body
     * @return Query Result body
     */
    fun executeDelete(query: Query): Int = persistenceManager.executeDelete(query)

    /**
     * Exists
     *
     * @param body Entity Request Body
     * @return Whether the entity exists or not
     */
    fun existsWithId(body: EntityFindRequest): Boolean = find(body) != null

    /**
     * Save Deferred Relationships
     *
     * @param request Save Relationship Request Body
     */
    fun saveRelationshipsForEntity(request: SaveRelationshipRequestBody) {
        val clazz = ApplicationClassFinder.forName(request.type, context)
        val entity = objectMapper.convertValue(request.entity, clazz) as IManagedEntity

        persistenceManager.saveRelationshipsForEntity(entity, request.relationship!!, request.identifiers!!)
    }

    /**
     * Returns the number of items matching the query criteria
     *
     * @param query Query request body
     * @return long value of number of items matching criteria
     * @since 1.3.0 Added as enhancement for git issue #71
     */
    fun countForQuery(query: Query): Long = persistenceManager.countForQuery(query)
}
