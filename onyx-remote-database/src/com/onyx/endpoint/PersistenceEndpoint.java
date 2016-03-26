package com.onyx.endpoint;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.request.pojo.*;
import com.onyx.util.AttributeField;
import com.onyx.util.ObjectUtil;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;

import java.util.List;

/**
 * Created by timothy.osborn on 5/13/15.
 */
public class PersistenceEndpoint implements ServerEndpoint {

    private final PersistenceManager persistenceManager;

    // Object Utility for reflection
    public static final ObjectUtil objectUtil = ObjectUtil.getInstance();

    /**
     * Constructor
     *
     * @param persistenceManager
     */
    public PersistenceEndpoint(PersistenceManager persistenceManager)
    {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Handle Tokens for persistence
     * @param token
     * @throws Exception
     */
    @Override
    public void handleToken(RequestToken token, WebSocketChannel channel, WebSocketCallback<Void> callback) throws Exception {
        final RequestTokenType requestType = RequestTokenType.values()[token.getType()];

        switch (requestType)
        {

            case PING:
                break;
            case SAVE:
                persistenceManager.saveEntity((IManagedEntity) token.getPayload());
                break;
            case BATCH_SAVE:
                persistenceManager.saveEntities((List) token.getPayload());
                token.setPayload(null);
                break;
            case DELETE:
                token.setPayload(persistenceManager.deleteEntity((IManagedEntity) token.getPayload()));
                break;
            case FIND:
                persistenceManager.find((IManagedEntity) token.getPayload());
                break;
            case FIND_BY_ID:
                final EntityRequestBody body = (EntityRequestBody)token.getPayload();
                token.setPayload(persistenceManager.findById(Class.forName(body.getType()), body.getId()));
                break;
            case FIND_BY_ID_IN_PARTITION:
                final EntityRequestBody findInPartitionBody = (EntityRequestBody)token.getPayload();
                token.setPayload(persistenceManager.findByIdInPartition(Class.forName(findInPartitionBody.getType()), findInPartitionBody.getId(), findInPartitionBody.getPartitionId()));
                break;
            case EXISTS:
                token.setPayload(persistenceManager.exists((IManagedEntity) token.getPayload()));
                break;
            case EXISTS_IN_PARTITION:
                final EntityRequestBody existsInPartitionBody = (EntityRequestBody)token.getPayload();
                token.setPayload(persistenceManager.exists((IManagedEntity) existsInPartitionBody.getEntity(), existsInPartitionBody.getPartitionId()));
                break;
            case BATCH_DELETE:
                persistenceManager.deleteEntities((List)token.getPayload());
                token.setPayload(null);
                break;
            case QUERY:
                QueryResultResponseBody queryPayload = new QueryResultResponseBody();
                queryPayload.setResultList(persistenceManager.executeQuery((Query) token.getPayload()));
                queryPayload.setMaxResults(((Query) token.getPayload()).getResultsCount());
                token.setPayload(queryPayload);
                break;
            case QUERY_UPDATE:
                QueryResultResponseBody queryUpdatePayload = new QueryResultResponseBody();
                queryUpdatePayload.setResults(persistenceManager.executeUpdate((Query) token.getPayload()));
                queryUpdatePayload.setMaxResults(((Query) token.getPayload()).getResultsCount());
                token.setPayload(queryUpdatePayload);
                break;
            case QUERY_DELETE:
                QueryResultResponseBody queryDeletePayload = new QueryResultResponseBody();
                queryDeletePayload.setResults(persistenceManager.executeDelete((Query) token.getPayload()));
                queryDeletePayload.setMaxResults(((Query) token.getPayload()).getResultsCount());
                token.setPayload(queryDeletePayload);
                break;
            case QUERY_LAZY:
                QueryResultResponseBody queryLazyPayload = new QueryResultResponseBody();
                queryLazyPayload.setResultList(persistenceManager.executeQuery((Query) token.getPayload()));
                queryLazyPayload.setMaxResults(((Query) token.getPayload()).getResultsCount());
                token.setPayload(queryLazyPayload);
                break;
            case INITIALIZE:
                EntityInitializeBody initPayload = (EntityInitializeBody)token.getPayload();
                Class parentClass = Class.forName(initPayload.getEntityType());
                IManagedEntity parentEntity = null;
                if(initPayload.getPartitionId() == null || (initPayload.getPartitionId() instanceof String) && initPayload.getPartitionId().equals(""))
                {
                    parentEntity = persistenceManager.findById(parentClass, initPayload.getEntityId());
                }
                else
                {
                    parentEntity = persistenceManager.findByIdInPartition(parentClass, initPayload.getEntityId(), initPayload.getPartitionId());
                }
                persistenceManager.initialize(parentEntity, initPayload.getAttribute());
                token.setPayload(objectUtil.getAttribute(new AttributeField(objectUtil.getField(parentEntity.getClass(), initPayload.getAttribute())), parentEntity));
                break;
            case SAVE_RELATIONSHIPS:
                SaveRelationshipRequestBody saveRelationshipRequestBody = (SaveRelationshipRequestBody) token.getPayload();
                persistenceManager.saveRelationshipsForEntity((IManagedEntity)saveRelationshipRequestBody.getEntity(), saveRelationshipRequestBody.getRelationship(), saveRelationshipRequestBody.getIdentifiers());
                token.setPayload(null);
                break;
            case FIND_BY_REFERENCE_ID:
                final EntityRequestBody referenceBody = (EntityRequestBody)token.getPayload();
                token.setPayload(persistenceManager.getWithReferenceId(Class.forName(referenceBody.getType()), (long) referenceBody.getId()));
                break;
            case FIND_WITH_PARTITION_ID:
                final EntityRequestBody partitionReferenceBody = (EntityRequestBody)token.getPayload();
                token.setPayload(persistenceManager.findByIdWithPartitionId(Class.forName(partitionReferenceBody.getType()), partitionReferenceBody.getId(), Long.valueOf(partitionReferenceBody.getPartitionId())));
                break;
        }
    }
}
