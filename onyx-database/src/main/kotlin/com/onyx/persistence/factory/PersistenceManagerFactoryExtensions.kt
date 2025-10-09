package com.onyx.persistence.factory

import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemPartitionEntry
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.and
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.notStartsWith
import com.onyx.persistence.stream.QueryStream
import kotlin.reflect.KClass

/**
 * Clone the contents of this database into another persistence manager factory.
 *
 * The method iterates over every managed type and streams each partition so that
 * the destination persistence manager receives the entities in the same order.
 *
 * @param destinationFactory persistence manager factory pointing at the new database directory
 */
fun PersistenceManagerFactory.cloneTo(destinationFactory: PersistenceManagerFactory) {
    val sourceManager = this.persistenceManager
    val destinationManager = destinationFactory.persistenceManager
    val schemaContext = this.schemaContext

    val systemEntities = schemaContext.serializedPersistenceManager
        .from(SystemEntity::class)
        .where(("isLatestVersion" eq true) and ("name" notStartsWith "com.onyx.entity.System"))
        .list<SystemEntity>()

    systemEntities.forEach { systemEntity ->
        val entityClass = runCatching { systemEntity.type(schemaContext.contextId) }
            .getOrNull()
            ?.takeIf { IManagedEntity::class.java.isAssignableFrom(it) }
            ?: return@forEach

        val partitions = schemaContext.getAllPartitionsSafely(entityClass)

        if (partitions.isEmpty()) {
            streamAndSave(sourceManager, destinationManager, entityClass.kotlin)
        } else {
            partitions.forEach { partition ->
                streamAndSave(sourceManager, destinationManager, entityClass.kotlin, partition.value)
            }
        }
    }
}

private fun streamAndSave(
    sourceManager: PersistenceManager,
    destinationManager: PersistenceManager,
    entityType: KClass<*>,
    partition: Any? = null,
) {
    val queryBuilder = sourceManager.from(entityType)
    if (partition != null) {
        queryBuilder.inPartition(partition)
    }

    val query = queryBuilder.query
    sourceManager.stream(query, object : QueryStream<IManagedEntity> {
        override fun accept(entity: IManagedEntity, persistenceManager: PersistenceManager): Boolean {
            destinationManager.saveEntity(entity)
            return true
        }
    })
}

private fun SchemaContext.getAllPartitionsSafely(entityClass: Class<*>): List<SystemPartitionEntry> = runCatching {
    getAllPartitions(entityClass)
}.getOrElse { emptyList() }
