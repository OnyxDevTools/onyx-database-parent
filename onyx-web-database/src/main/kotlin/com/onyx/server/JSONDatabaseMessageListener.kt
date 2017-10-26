package com.onyx.server

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.onyx.buffer.BufferPool
import com.onyx.endpoint.WebPersistenceEndpoint
import com.onyx.exception.OnyxException
import com.onyx.exception.UnknownDatabaseException
import com.onyx.extension.withBuffer
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.request.pojo.*
import com.onyx.serialization.CustomAnnotationInspector
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import java.nio.ByteBuffer
import java.util.concurrent.locks.LockSupport

/**
 * Created by Tim Osborn on 12/7/15.
 *
 *
 * The purpose of this class is to be the entry point for JSON interface for the persistence api
 */
class JSONDatabaseMessageListener(persistenceManager: PersistenceManager, context: SchemaContext) : HttpHandler {

    private val objectMapper = ObjectMapper()

    private val webPersistenceEndpoint: WebPersistenceEndpoint

    private val READ_TIMEOUT = (60 * 1000).toLong()

    init {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.setAnnotationIntrospector(CustomAnnotationInspector())
        this.webPersistenceEndpoint = WebPersistenceEndpoint(persistenceManager, this.objectMapper, context)
    }

    /**
     * This method gets the corresponding class to deserialize the packet
     *
     * @param path String endpoint for rest service
     * @return class to deserialize packet to
     */
    private fun getClassForEndpoint(path: RestServicePath): Class<*> = when (path) {
        RestServicePath.SAVE, RestServicePath.DELETE, RestServicePath.FIND, RestServicePath.FIND_BY_PARTITION, RestServicePath.FIND_BY_PARTITION_REFERENCE, RestServicePath.EXISTS -> EntityRequestBody::class.java
        RestServicePath.EXECUTE, RestServicePath.EXECUTE_DELETE, RestServicePath.EXECUTE_UPDATE, RestServicePath.QUERY_COUNT -> EntityQueryBody::class.java
        RestServicePath.INITIALIZE -> EntityInitializeBody::class.java
        RestServicePath.BATCH_DELETE, RestServicePath.BATCH_SAVE -> EntityListRequestBody::class.java
        RestServicePath.SAVE_RELATIONSHIPS -> SaveRelationshipRequestBody::class.java
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
    @Throws(OnyxException::class, ClassNotFoundException::class, InstantiationException::class, IllegalAccessException::class)
    private fun invokeHandler(path: RestServicePath, body: Any): Any? {
        when (path) {
            RestServicePath.SAVE -> return webPersistenceEndpoint.save(body as EntityRequestBody)
            RestServicePath.DELETE -> return webPersistenceEndpoint.delete(body as EntityRequestBody)
            RestServicePath.FIND -> return webPersistenceEndpoint[body as EntityRequestBody]
            RestServicePath.FIND_BY_PARTITION -> return webPersistenceEndpoint.findWithPartitionId(body as EntityRequestBody)
            RestServicePath.FIND_BY_PARTITION_REFERENCE -> return webPersistenceEndpoint.findByPartitionReference(body as EntityRequestBody)
            RestServicePath.EXISTS -> return webPersistenceEndpoint.exists(body as EntityRequestBody)
            RestServicePath.EXECUTE -> return webPersistenceEndpoint.executeQuery(body as EntityQueryBody)
            RestServicePath.EXECUTE_DELETE -> return webPersistenceEndpoint.executeDelete(body as EntityQueryBody)
            RestServicePath.EXECUTE_UPDATE -> return webPersistenceEndpoint.executeUpdate(body as EntityQueryBody)
            RestServicePath.INITIALIZE -> return webPersistenceEndpoint.initialize(body as EntityInitializeBody)
            RestServicePath.BATCH_DELETE -> webPersistenceEndpoint.deleteEntities(body as EntityListRequestBody)
            RestServicePath.BATCH_SAVE -> webPersistenceEndpoint.saveEntities(body as EntityListRequestBody)
            RestServicePath.SAVE_RELATIONSHIPS -> webPersistenceEndpoint.saveRelationshipsForEntity(body as SaveRelationshipRequestBody)
            RestServicePath.QUERY_COUNT -> return webPersistenceEndpoint.countForQuery(body as EntityQueryBody)
        }
        return null
    }

    @Throws(Exception::class)
    override fun handleRequest(exchange: HttpServerExchange) {

        val channel = exchange.requestChannel

        if (channel == null)
            exchange.endExchange()
        else {
            val runnable = {
                try {
                    val stringPath = exchange.relativePath
                    val path = RestServicePath.valueOfPath(stringPath)
                    val bodyType = getClassForEndpoint(path)
                    val buffer = BufferPool.allocateAndLimit(exchange.requestContentLength.toInt())
                    var bytes: ByteArray? = null

                    withBuffer(buffer) {
                        val time = System.currentTimeMillis()
                        while (buffer.hasRemaining()) {
                            channel.read(buffer)
                            if (buffer.remaining() > 0 && time + READ_TIMEOUT > System.currentTimeMillis()) {
                                LockSupport.parkNanos(100)
                            } else
                                break
                        }

                        bytes = ByteArray(buffer.limit())
                        buffer.rewind()
                        buffer.get(bytes)
                    }

                    val requestBody = objectMapper.readValue(bytes, bodyType)
                    //            async {
                    val response = invokeHandler(path, requestBody)
                    sendResponse(exchange, response, 200)
                    //            }

                } catch (entityException: OnyxException) {
                    val response = ExceptionResponse(entityException, entityException.javaClass.name)
                    try {
                        sendResponse(exchange, response, 303)
                    } catch (e: JsonProcessingException) {
                        // Swallow response
                    }

                } catch (e: Exception) {
                    val response = ExceptionResponse(UnknownDatabaseException(e), UnknownDatabaseException::class.java.name)
                    try {
                        sendResponse(exchange, response, 303)
                    } catch (jsonEx: JsonProcessingException) {
                        // Swallow response
                    }
                } finally {
                    // End the exchange
                    exchange.endExchange()
                }
            }
            exchange.dispatch(runnable)
        }
    }


    /**
     * Send a generic response back to client
     *
     * @param exchange HttpServerExchange contains the connection
     * @param response Serializable response value
     * @throws JsonProcessingException Error parsing json
     */
    @Throws(JsonProcessingException::class)
    private fun sendResponse(exchange: HttpServerExchange, response: Any?, responseCode: Int) {
        // Get Response bytes
        val responseBytes = objectMapper.writeValueAsBytes(response)

        // Prepare headers
        exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/json")

        // Set OK or ERROR aka SEE_OTHER
        exchange.responseCode = responseCode

        // Package response in a buffer
        val responseBuffer = ByteBuffer.allocate(responseBytes.size)
        responseBuffer.put(responseBytes)
        responseBuffer.rewind()

        // Send response
        exchange.responseSender.send(responseBuffer)
    }

}