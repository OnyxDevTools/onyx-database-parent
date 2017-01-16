package streams;

import com.onyx.exception.EntityException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.stream.QueryMapStream;
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete;

import java.util.Map;

/**
 * Created by tosborn1 on 7/14/16.
 */
public class BasicQueryMapStream implements QueryMapStream{

    @Override
    public void accept(Object obj, PersistenceManager persistenceManager) {

        // Modify the entity structure
        final Map entityMap = (Map)obj;
        entityMap.put("correlation", 55);

        // Remap to the entity so that we can persist it with the changes to the dictionary after we manipulate it.
        final ImmutableSequenceIdentifierEntityForDelete freshEntity = new ImmutableSequenceIdentifierEntityForDelete();
        freshEntity.fromMap(entityMap, persistenceManager.getContext());

        // Save the entity
        try {
            persistenceManager.saveEntity(freshEntity);
        } catch (EntityException e) {
            e.printStackTrace();
        }
    }

}
