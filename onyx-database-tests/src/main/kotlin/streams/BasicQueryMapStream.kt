package streams

import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.stream.QueryMapStream
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete

/**
 * Created by Tim Osborn on 7/14/16.
 */
class BasicQueryMapStream : QueryMapStream<Map<String, Any?>> {

    override fun accept(entity: Map<String, Any?>, persistenceManager: PersistenceManager) {

        // Modify the entity structure
        val mutableMap = entity.toMutableMap()
        mutableMap.put("correlation", 55)

        // Remap to the entity so that we can persist it with the changes to the dictionary after we manipulate it.
        val freshEntity = ImmutableSequenceIdentifierEntityForDelete()
        @Suppress("UNCHECKED_CAST")
        freshEntity.fromMap(mutableMap as Map<String, Any>, persistenceManager.context)

        // Save the entity
        try {
            persistenceManager.saveEntity<IManagedEntity>(freshEntity)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

    }

}
