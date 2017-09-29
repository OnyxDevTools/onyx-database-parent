package com.onyx.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onyx.buffer.BufferPool;
import com.onyx.buffer.BufferStream;
import com.onyx.endpoint.WebPersistenceEndpoint;
import com.onyx.exception.OnyxException;
import com.onyx.exception.UnknownDatabaseException;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.request.pojo.*;
import com.onyx.serialization.CustomAnnotationInspector;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.channels.StreamSourceChannel;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by tosborn1 on 12/7/15.
 * <p>
 * The purpose of this class is to be the entry point for JSON interface for the persistence api
 */
public class JSONDatabaseMessageListener implements HttpHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebPersistenceEndpoint webPersistenceEndpoint;

    private final long READ_TIMEOUT = 60 * 1000;

    /**
     * Constructor
     *
     * @param persistenceManager Local Persistence Manager
     * @param context            Local Schema Context
     */
    public JSONDatabaseMessageListener(final PersistenceManager persistenceManager, final SchemaContext context) {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setAnnotationIntrospector(new CustomAnnotationInspector());
        this.webPersistenceEndpoint = new WebPersistenceEndpoint(persistenceManager, this.objectMapper, context);
    }

    /**
     * This method gets the corresponding class to deserialize the packet
     *
     * @param path String endpoint for rest service
     * @return class to deserialize packet to
     */
    private Class getClassForEndpoint(final RestServicePath path) {
        Class classToSerialize = null;

        switch (path) {
            case SAVE:
            case DELETE:
            case FIND:
            case FIND_BY_PARTITION:
            case FIND_BY_REFERENCE:
            case FIND_BY_PARTITION_REFERENCE:
            case EXISTS:
                classToSerialize = EntityRequestBody.class;
                break;
            case EXECUTE:
            case EXECUTE_DELETE:
            case EXECUTE_UPDATE:
            case QUERY_COUNT:
                classToSerialize = EntityQueryBody.class;
                break;
            case INITIALIZE:
                classToSerialize = EntityInitializeBody.class;
                break;
            case BATCH_DELETE:
            case BATCH_SAVE:
                classToSerialize = EntityListRequestBody.class;
                break;
            case SAVE_RELATIONSHIPS:
                classToSerialize = SaveRelationshipRequestBody.class;
                break;
        }

        return classToSerialize;
    }


    /**
     * Invoke method handler with a given path and pass the request body into it
     *
     * @param path request path
     * @param body package body
     * @return Response from method invocation
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws OnyxException        General entity exception
     */
    private Object invokeHandler(RestServicePath path, final Object body) throws OnyxException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        switch (path) {
            case SAVE:
                return webPersistenceEndpoint.save((EntityRequestBody) body);
            case DELETE:
                return webPersistenceEndpoint.delete((EntityRequestBody) body);
            case FIND:
                return webPersistenceEndpoint.get((EntityRequestBody) body);
            case FIND_BY_PARTITION:
                return webPersistenceEndpoint.findWithPartitionId((EntityRequestBody) body);
            case FIND_BY_REFERENCE:
                return webPersistenceEndpoint.findByReferenceId((EntityRequestBody) body);
            case FIND_BY_PARTITION_REFERENCE:
                return webPersistenceEndpoint.findByPartitionReference((EntityRequestBody) body);
            case EXISTS:
                return webPersistenceEndpoint.exists((EntityRequestBody) body);
            case EXECUTE:
                return webPersistenceEndpoint.executeQuery((EntityQueryBody) body);
            case EXECUTE_DELETE:
                return webPersistenceEndpoint.executeDelete((EntityQueryBody) body);
            case EXECUTE_UPDATE:
                return webPersistenceEndpoint.executeUpdate((EntityQueryBody) body);
            case INITIALIZE:
                return webPersistenceEndpoint.initialize((EntityInitializeBody) body);
            case BATCH_DELETE:
                webPersistenceEndpoint.deleteEntities((EntityListRequestBody) body);
                break;
            case BATCH_SAVE:
                webPersistenceEndpoint.saveEntities((EntityListRequestBody) body);
                break;
            case SAVE_RELATIONSHIPS:
                webPersistenceEndpoint.saveRelationshipsForEntity((SaveRelationshipRequestBody) body);
                break;
            case QUERY_COUNT:
                return webPersistenceEndpoint.countForQuery((EntityQueryBody) body);
        }
        return null;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        StreamSourceChannel channel;
        do {
            channel = exchange.getRequestChannel();
            if (channel != null) {
                final StreamSourceChannel myChannel = channel;


                final Runnable runnable = () -> {
                    try {
                        final String stringPath = exchange.getRelativePath();
                        final RestServicePath path = RestServicePath.Companion.valueOfPath(stringPath);
                        final Class bodyType = getClassForEndpoint(path);
                        final ByteBuffer buffer = BufferPool.INSTANCE.allocateAndLimit((int) exchange.getRequestContentLength());
                        byte[] bytes = null;

                        try {
                            long time = System.currentTimeMillis();
                            while (buffer.remaining() > 0) {
                                myChannel.read(buffer);
                                if (buffer.remaining() > 0
                                        && (time + READ_TIMEOUT) > System.currentTimeMillis()) {
                                    LockSupport.parkNanos(100);
                                } else
                                    break;
                            }

                            bytes = new byte[buffer.limit()];
                            buffer.rewind();
                            buffer.get(bytes);
                        } finally {
                            BufferPool.INSTANCE.recycle(buffer);
                        }

                        final Object requestBody = objectMapper.readValue(bytes, bodyType);
                        final Object response = invokeHandler(path, requestBody);

                        sendResponse(exchange, response, 200);

                    } catch (OnyxException entityException) {
                        final ExceptionResponse response = new ExceptionResponse(entityException, entityException.getClass().getName());
                        try {
                            sendResponse(exchange, response, 303);
                        } catch (JsonProcessingException e) {
                            // Swallow response
                        }
                    } catch (Exception e) {
                        final ExceptionResponse response = new ExceptionResponse(new UnknownDatabaseException(e), UnknownDatabaseException.class.getName());
                        try {
                            sendResponse(exchange, response, 303);
                        } catch (JsonProcessingException jsonEx) {
                            // Swallow response
                        }
                    } finally {
                        // End the exchange
                        /*try {
                            myChannel.close();
                        } catch (IOException e) {
                            // No error handling here
                        }*/
                        exchange.endExchange();
                    }
                };

                exchange.dispatch(runnable);

            }
        } while (channel != null);

    }

    /**
     * Send a generic response back to client
     *
     * @param exchange HttpServerExchange contains the connection
     * @param response Serializable response value
     * @throws JsonProcessingException Error parsing json
     */
    private void sendResponse(final HttpServerExchange exchange, final Object response, int responseCode) throws JsonProcessingException {
        // Get Response bytes
        byte[] responseBytes = objectMapper.writeValueAsBytes(response);

        // Prepare headers
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

        // Set OK or ERROR aka SEE_OTHER
        exchange.setResponseCode(responseCode);

        // Package response in a buffer
        ByteBuffer responseBuffer = ByteBuffer.allocate(responseBytes.length);
        responseBuffer.put(responseBytes);
        responseBuffer.rewind();

        // Send response
        exchange.getResponseSender().send(responseBuffer);
    }

}
