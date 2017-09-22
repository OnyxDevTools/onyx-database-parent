package streams;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.stream.QueryStream;
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete;

/**
 * Created by tosborn1 on 7/14/16.
 */
public class BasicQueryStream implements QueryStream {

    public BasicQueryStream() {

    }

    @Override
    public void accept(Object entity, PersistenceManager persistenceManager) {
        ((ImmutableSequenceIdentifierEntityForDelete)entity).correlation = 99;
        try {
            persistenceManager.saveEntity((IManagedEntity)entity);
        } catch (OnyxException e) {
            e.printStackTrace();
        }
    }
}
