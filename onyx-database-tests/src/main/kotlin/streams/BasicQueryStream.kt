package streams

import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.stream.QueryStream
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete

/**
 * Created by Tim Osborn on 7/14/16.
 */
class BasicQueryStream : QueryStream<ImmutableSequenceIdentifierEntityForDelete> {

    override fun accept(entity: ImmutableSequenceIdentifierEntityForDelete, persistenceManager: PersistenceManager) {
        entity.correlation = 99
        persistenceManager.saveEntity(entity)
    }
}
