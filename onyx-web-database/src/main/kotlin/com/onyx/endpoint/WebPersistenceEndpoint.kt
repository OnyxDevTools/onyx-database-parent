package com.onyx.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.onyx.exception.EntityClassNotFoundException
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.extension.common.*
import com.onyx.interactors.classfinder.ApplicationClassFinder
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
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
     * @throws OnyxException Generic exception
     * @throws ClassNotFoundException Not found when attempting to reflect
     */
    @Throws(OnyxException::class, ClassNotFoundException::class)
    fun save(request: EntityRequestBody): IManagedEntity {
        val clazz = ApplicationClassFinder.forName(request.type!!)
        val entity = objectMapper.convertValue(request.entity, clazz)
        persistenceManager.saveEntity<IManagedEntity>(entity as IManagedEntity)

        return entity
    }

    /**
     * Find Entity with Reference Id and partition
     *
     * @param request Entity Request Body
     * @return Populated entity or null if not found
     * @throws OnyxException General entity exception
     */
    @Throws(OnyxException::class)
    fun findByPartitionReference(request: EntityRequestBody): IManagedEntity? {
        var clazz: Class<*>? = null
        clazz = try {
            ApplicationClassFinder.forName(request.type)
        } catch (e: ClassNotFoundException) {
            throw EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND, clazz!!)
        }

        val partitionId = java.lang.Long.valueOf(request.partitionId)!!
        val reference = Reference(partitionId, request.id as Long)
        return persistenceManager.getWithReference(clazz, reference)
    }


    /**
     * Find Entity
     *
     * @param request Entity Request Body
     * @return Hydrated entity
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws OnyxException General entity exception
     */
    @Throws(ClassNotFoundException::class, IllegalAccessException::class, InstantiationException::class, OnyxException::class)
    operator fun get(request: EntityRequestBody): IManagedEntity {
        val clazz = ApplicationClassFinder.forName(request.type)
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
     * Find Entity within a partition
     *
     * @param request Entity Request Body
     * @return Hydrated entity
     * @throws OnyxException General entity exception
     */
    @Throws(OnyxException::class)
    fun findWithPartitionId(request: EntityRequestBody): IManagedEntity {
        val clazz: Class<*> = try {
            ApplicationClassFinder.forName(request.type)
        } catch (e: ClassNotFoundException) {
            throw EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND)
        }

        val partitionId = java.lang.Long.valueOf(request.partitionId)!!
        return persistenceManager.findByIdWithPartitionId(clazz, request.id!!, partitionId)
    }


    /**
     * Delete Entity
     *
     * @param request Entity Request Body
     * @return Whether the entity was deleted or not
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws OnyxException General entity exception
     */
    @Throws(ClassNotFoundException::class, IllegalAccessException::class, InstantiationException::class, OnyxException::class)
    fun delete(request: EntityRequestBody): Boolean {
        val clazz = ApplicationClassFinder.forName(request.type)
        val entity: IManagedEntity
        entity = objectMapper.convertValue(request.entity, clazz) as IManagedEntity

        return persistenceManager.deleteEntity(entity)
    }

    /**
     * Execute Query
     *
     * @param request Query Body
     * @return Query Result body
     * @throws OnyxException Entity exception while executing query
     */
    @Throws(OnyxException::class)
    fun executeQuery(request: EntityQueryBody): QueryResultResponseBody {
        val results = persistenceManager.executeQuery<Any>(request.query!!)
        return QueryResultResponseBody(request.query!!.resultsCount, results.toMutableList())
    }

    /**
     * Initialize Relationship.  Called upon a to many relationship
     *
     * @param request Initialize Body Error
     * @return List of relationship objects
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws OnyxException General entity exception
     */
    @Throws(OnyxException::class, IllegalAccessException::class, InstantiationException::class, ClassNotFoundException::class)
    fun initialize(request: EntityInitializeBody): Any? {
        val clazz = ApplicationClassFinder.forName(request.entityType)
        val entity = clazz.newInstance() as IManagedEntity

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
     * @throws OnyxException Error saving entities
     * @throws ClassNotFoundException Cannot reflect cause entity not found
     */
    @Throws(OnyxException::class, ClassNotFoundException::class)
    fun saveEntities(request: EntityListRequestBody) {
        val clazz = ApplicationClassFinder.forName(request.type)

        val javaType = objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
        val entities: List<IManagedEntity>

        entities = try {
            objectMapper.readValue(request.entities, javaType)
        } catch (e: IOException) {
            throw EntityClassNotFoundException(OnyxException.UNKNOWN_EXCEPTION)
        }

        persistenceManager.saveEntities(entities)
    }

    /**
     * Batch Delete
     *
     * @param request Entity List body
     * @throws OnyxException Exception when deleting entities
     * @throws ClassNotFoundException Cannot find entity type
     */
    @Throws(OnyxException::class, ClassNotFoundException::class)
    fun deleteEntities(request: EntityListRequestBody) {
        val clazz = ApplicationClassFinder.forName(request.type)

        val javaType = objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
        val entities: List<IManagedEntity>

        entities = try {
            objectMapper.readValue(request.entities, javaType)
        } catch (e: IOException) {
            throw EntityClassNotFoundException(OnyxException.UNKNOWN_EXCEPTION)
        }

        persistenceManager.deleteEntities(entities)
    }

    /**
     * Execute Update
     *
     * @param request Entity Query Body
     * @return Query Result body
     * @throws OnyxException Error executing update
     */
    @Throws(OnyxException::class)
    fun executeUpdate(request: EntityQueryBody): QueryResultResponseBody {
        val results = persistenceManager.executeUpdate(request.query!!)
        return QueryResultResponseBody(request.query!!.maxResults, results)
    }

    /**
     * Execute Delete
     *
     * @param request Entity Query Body
     * @return Query Result body
     * @throws OnyxException Error executing delete
     */
    @Throws(OnyxException::class)
    fun executeDelete(request: EntityQueryBody): QueryResultResponseBody {
        val results = persistenceManager.executeDelete(request.query!!)
        return QueryResultResponseBody(request.query!!.maxResults, results)
    }

    /**
     * Exists
     *
     * @param body Entity Request Body
     * @return Whether the entity exists or not
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws OnyxException General entity exception
     */
    @Throws(ClassNotFoundException::class, IllegalAccessException::class, InstantiationException::class, OnyxException::class)
    fun exists(body: EntityRequestBody): Boolean {
        val clazz = ApplicationClassFinder.forName(body.type)
        val entity = objectMapper.convertValue(body.entity, clazz) as IManagedEntity
        return persistenceManager.exists(entity)
    }

    /**
     * Save Deferred Relationships
     *
     * @param request Save Relationship Request Body
     * @throws OnyxException Generic Entity exception trying to save relationships
     * @throws ClassNotFoundException Entity type not found
     */
    @Throws(OnyxException::class, ClassNotFoundException::class)
    fun saveRelationshipsForEntity(request: SaveRelationshipRequestBody) {
        val clazz = ApplicationClassFinder.forName(request.type)
        val entity = objectMapper.convertValue(request.entity, clazz) as IManagedEntity

        persistenceManager.saveRelationshipsForEntity(entity, request.relationship!!, request.identifiers!!)
    }

    /**
     * Returns the number of items matching the query criteria
     *
     * @param body Query request body
     * @return long value of number of items matching criteria
     * @throws OnyxException General query exception
     * @since 1.3.0 Added as enhancement for git issue #71
     */
    @Throws(OnyxException::class)
    fun countForQuery(body: EntityQueryBody): Long = persistenceManager.countForQuery(body.query!!)
}
