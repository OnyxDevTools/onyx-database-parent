package com.onyx.persistence.manager.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.onyx.entity.SystemError;
import com.onyx.exception.EntityException;
import com.onyx.persistence.context.impl.WebSchemaContext;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.request.ProgressHttpEntityWrapper;
import com.onyx.request.pojo.EntityRequestBody;
import com.onyx.request.pojo.QueryResultResponseBody;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.springframework.http.*;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Base Methods for communicating to an Onyx Web Database
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
public abstract class AbstractWebPersistenceManager
{

    protected WebSchemaContext context;


    public static final String ENTITY = "/entity";

    public static final String SAVE = "/saveEntity";
    public static final String FIND = "/find";
    public static final String EXISTS = "/exists";
    public static final String DELETE = "/deleteEntity";
    public static final String INITIALIZE = "/initialize";
    public static final String EXECUTE_QUERY = "/execute";
    public static final String EXECUTE_UPDATE_QUERY = "/executeUpdate";
    public static final String EXECUTE_DELETE_QUERY = "/executeDelete";

    public static final String BATCH_SAVE = "/batchSave";
    public static final String BATCH_DELETE = "/batchDelete";

    public static final String SAVE_RELATIONSHIPS = "/saveRelationships";
    public static final String FIND_BY_REFERENCE_ID = "/findByReferenceId";

    public static final String FIND_WITH_PARTITION_ID = "/findWithPartitionId";

    protected ObjectMapper objectMapper;

    protected RestTemplate restTemplate;

    protected ExecutorService executorService;

    protected HttpClient httpClient;

    protected PersistenceManagerFactory factory;

    /**
     * Perform Operation Call
     *
     * @param path URI Path
     * @param elementType Entity Type
     * @param returnType Return Type
     * @param body Packet
     * @return Generic Object
     * @throws EntityException Generic Database Exception thrown
     */
    protected Object performCall(String path, Class elementType, Class returnType, Object body) throws EntityException
    {
        HttpEntity<String> requestEntity = null;
        try
        {
            requestEntity = new HttpEntity<String>(objectMapper.writeValueAsString(body),getHeaders());
        } catch (JsonProcessingException e)
        {
            e.printStackTrace();
        }

        Object response = null;

        Object retVal = null;

        ResponseEntity res = null;

        if(returnType == List.class)
            res = restTemplate.exchange(path, HttpMethod.POST, requestEntity, String.class);
        else
            res = restTemplate.exchange(path, HttpMethod.POST, requestEntity, Object.class);

        if(res.getStatusCode() == HttpStatus.OK)
        {

            if(returnType == QueryResultResponseBody.class)
            {
                response = objectMapper.convertValue(res.getBody(), returnType);

                QueryResultResponseBody responseValue = (QueryResultResponseBody) response;
                if(responseValue.getResultList() != null)
                {
                    for(int i = 0; i < responseValue.getResultList().size(); i++)
                    {
                        responseValue.getResultList().set(i, objectMapper.convertValue(responseValue.getResultList().get(i), elementType));
                    }
                }
            }
            else if(returnType == List.class)
            {
                final CollectionType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
                try
                {
                    response = objectMapper.readValue((String)res.getBody(), javaType);
                } catch (IOException e)
                {
                    e.printStackTrace();
                }

            }
            else
            {
                response = objectMapper.convertValue(res.getBody(), returnType);
            }
        }
        else if(res.getStatusCode() == HttpStatus.SEE_OTHER)
        {

            String exception = (String)((LinkedHashMap)res.getBody()).get("exceptionType");
            EntityException e = null;
            try
            {
                Class exceptionClass = Class.forName(exception);
                e = (EntityException)objectMapper.convertValue(res.getBody(), exceptionClass);
                throw e;
            } catch (ClassNotFoundException ignore)
            {}
        }


        return response;
    }

    /**
     * Get HTTP Headers
     *
     * @return HTTP Headers
     */
    protected HttpHeaders getHeaders()
    {
        final HttpHeaders headers = new HttpHeaders();
        return headers;
    }

    /**
     * Global Fault Handler
     */
    final protected Consumer<SystemError> globalFaultHandler = (e) ->
    {

        if (e.getException() instanceof HttpClientErrorException)
        {
            // Authentication Failed, lets logout
            //serverController.showLoginPrompt(null);

            // TODO: Throw Connection Error
            return;
        }

        // TODO: Check to see if logging is avaialble
        EntityRequestBody body = new EntityRequestBody();
        body.setType(e.getException().getClass().getName());
        body.setEntity(e);

        try
        {
            this.performCall(context.getRemoteEndpoint() + ENTITY + SAVE, null, ((Object)e).getClass(), body);
        }
        catch (Exception newEx)
        {
            newEx.printStackTrace();
        }
    };

    /**
     * Global Result Handler
     */
    final protected Consumer<Object> globalResultHandler = (e) ->
    {
        // TODO: Log Exception
    };

    public SchemaContext getContext()
    {
        return context;
    }

    public void setContext(SchemaContext context)
    {
        this.context = (WebSchemaContext)context;
    }

    public PersistenceManagerFactory getFactory()
    {
        return factory;
    }

    public void setFactory(PersistenceManagerFactory factory)
    {
        this.factory = factory;
    }

    public ObjectMapper getObjectMapper()
    {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public RestTemplate getRestTemplate()
    {
        return restTemplate;
    }

    public void setRestTemplate(RestTemplate restTemplate)
    {
        this.restTemplate = restTemplate;
    }

    public ExecutorService getExecutorService()
    {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService)
    {
        this.executorService = executorService;
    }

    public HttpClient getHttpClient()
    {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public Consumer<SystemError> getGlobalFaultHandler()
    {
        return globalFaultHandler;
    }

    public Consumer<Object> getGlobalResultHandler()
    {
        return globalResultHandler;
    }
}
