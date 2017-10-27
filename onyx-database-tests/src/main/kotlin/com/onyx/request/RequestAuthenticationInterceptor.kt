package com.onyx.request

import com.onyx.persistence.factory.PersistenceManagerFactory
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.crypto.codec.Base64
import org.springframework.stereotype.Component

import java.io.IOException
import java.util.Arrays

@Component
class RequestAuthenticationInterceptor(private var factory: PersistenceManagerFactory) : ClientHttpRequestInterceptor, ApplicationContextAware {

    @Throws(IOException::class)
    override fun intercept(
            request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        val headers = request.headers
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = Arrays.asList(MediaType.APPLICATION_JSON)

        val encodedAuthorisation = String(Base64.encode(factory.credentials.toByteArray()))
        headers.add("Authorization", "Basic " + encodedAuthorisation)

        return execution.execute(request, body)
    }

    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        factory = applicationContext.getBean(PersistenceManagerFactory::class.java)
    }

}