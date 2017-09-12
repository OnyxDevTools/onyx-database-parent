package streams;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.stream.QueryMapStream;
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete;

import java.util.Map;

/**
 * Created by tosborn1 on 7/14/16.
 */
public class BasicQueryMapStream implements QueryMapStream<Map<String, ? >> {

    @Override
    public void accept(Map entityMap, PersistenceManager persistenceManager) {

        // Modify the entity structure
        entityMap.put("correlation", 55);

        // Remap to the entity so that we can persist it with the changes to the dictionary after we manipulate it.
        final ImmutableSequenceIdentifierEntityForDelete freshEntity = new ImmutableSequenceIdentifierEntityForDelete();
        freshEntity.fromMap(entityMap, persistenceManager.getContext());

        // Save the entity
        try {
            persistenceManager.saveEntity(freshEntity);
        } catch (OnyxException e) {
            e.printStackTrace();
        }
    }

}
