package com.onyx.persistence.manager.impl

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.onyx.exception.OnyxException
import com.onyx.interactors.classfinder.ApplicationClassFinder
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.request.pojo.QueryResultResponseBody
import org.apache.http.client.HttpClient
import org.springframework.http.*
import org.springframework.web.client.RestTemplate

import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService

/**
 * Base Methods for communicating to an Onyx Web Database
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 */
abstract class AbstractWebPersistenceManager : PersistenceManager {

    lateinit var objectMapper: ObjectMapper
    lateinit var restTemplate: RestTemplate
    lateinit var executorService: ExecutorService
    lateinit var httpClient: HttpClient
    lateinit var factory: PersistenceManagerFactory

    /**
     * Perform Operation Call
     *
     * @param path URI Path
     * @param elementType Entity Type
     * @param returnType Return Type
     * @param body Packet
     * @return Generic Object
     * @throws OnyxException Generic Database Exception thrown
     */
    @Throws(OnyxException::class)
    protected fun performCall(path: String, elementType: Class<*>?, returnType: Class<*>?, body: Any): Any? {
        var requestEntity: HttpEntity<String>? = null
        try {
            requestEntity = HttpEntity(objectMapper.writeValueAsString(body), headers)
        } catch (e: JsonProcessingException) {
            e.printStackTrace()
        }

        var response: Any? = null
        val res: ResponseEntity<*>?

        res = if (returnType == List::class.java)
            restTemplate.exchange(path, HttpMethod.POST, requestEntity, String::class.java)
        else
            restTemplate.exchange(path, HttpMethod.POST, requestEntity, Any::class.java)

        if (res!!.statusCode == HttpStatus.OK) {

            when (returnType) {
                QueryResultResponseBody::class.java -> {
                    response = objectMapper.convertValue<QueryResultResponseBody>(res.body, returnType)

                    val responseValue = response
                    for (i in 0 until responseValue.resultList.size) {
                        responseValue.resultList[i] = objectMapper.convertValue(responseValue.resultList[i], elementType)
                    }
                }
                List::class.java -> {
                    val javaType = objectMapper.typeFactory.constructCollectionType(List::class.java, elementType!!)
                    try {
                        response = objectMapper.readValue<Any>(res.body as String, javaType)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
                else -> response = objectMapper.convertValue(res.body, returnType)
            }
        } else if (res.statusCode == HttpStatus.SEE_OTHER) {

            val exception = (res.body as LinkedHashMap<*, *>)["exceptionType"] as String
            val e: OnyxException?
            try {
                val exceptionClass = ApplicationClassFinder.forName(exception)
                e = objectMapper.convertValue(res.body, exceptionClass) as OnyxException
                throw e
            } catch (ignore: ClassNotFoundException) {
            }

        }


        return response
    }

    /**
     * Get HTTP Headers
     *
     * @return HTTP Headers
     */
    private val headers: HttpHeaders
        get() = HttpHeaders()

    companion object {
        val SAVE = "/saveEntity"
        val FIND = "/find"
        val EXISTS = "/exists"
        val DELETE = "/deleteEntity"
        val INITIALIZE = "/initialize"
        val EXECUTE_QUERY = "/execute"
        val EXECUTE_UPDATE_QUERY = "/executeUpdate"
        val EXECUTE_DELETE_QUERY = "/executeDelete"
        val QUERY_COUNT = "/queryCount"
        val BATCH_SAVE = "/batchSave"
        val BATCH_DELETE = "/batchDelete"
        val SAVE_RELATIONSHIPS = "/saveRelationships"
        val FIND_BY_PARTITION_REFERENCE = "/findByPartitionReference"
        val FIND_WITH_PARTITION_ID = "/findWithPartitionId"
    }
}
