package com.onyx.request;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;

@Component
public class RequestAuthenticationInterceptor implements ClientHttpRequestInterceptor,ApplicationContextAware {

    public RequestAuthenticationInterceptor()
    {

    }

    public RequestAuthenticationInterceptor(PersistenceManagerFactory factory)
    {
        this.factory = factory;
    }

    protected PersistenceManagerFactory factory;

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        final HttpHeaders headers = request.getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        String encodedAuthorisation = new String(Base64.encode(factory.getCredentials().getBytes()));
        headers.add("Authorization", "Basic " + encodedAuthorisation);

        return execution.execute(request, body);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        factory = applicationContext.getBean(PersistenceManagerFactory.class);
    }

    public void setPersistenceManagerFactory(PersistenceManagerFactory factory)
    {
        this.factory = factory;
    }
}