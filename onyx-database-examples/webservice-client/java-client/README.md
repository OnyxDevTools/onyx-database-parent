# swagger-java-client

## Requirements

Building the API client library requires [Maven](https://maven.apache.org/) to be installed.

## Installation

To install the API client library to your local Maven repository, simply execute:

```shell
mvn install
```

To deploy it to a remote Maven repository instead, configure the settings of the repository and execute:

```shell
mvn deploy
```

Refer to the [official documentation](https://maven.apache.org/plugins/maven-deploy-plugin/usage.html) for more information.

### Maven users

Add this dependency to your project's POM:

```xml
<dependency>
    <groupId>io.swagger</groupId>
    <artifactId>swagger-java-client</artifactId>
    <version>1.0.0</version>
    <scope>compile</scope>
</dependency>
```

### Gradle users

Add this dependency to your project's build file:

```groovy
compile "io.swagger:swagger-java-client:1.0.0"
```

### Others

At first generate the JAR by executing:

    mvn package

Then manually install the following JARs:

* target/swagger-java-client-1.0.0.jar
* target/lib/*.jar

## Getting Started

Please follow the [installation](#installation) instruction and execute the following Java code:

```java

import io.swagger.client.*;
import io.swagger.client.auth.*;
import io.swagger.client.model.*;
import io.swagger.client.api.PersistenceApi;

import java.io.File;
import java.util.*;

public class PersistenceApiExample {

    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        
        // Configure HTTP basic authorization: basicAuth
        HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
        basicAuth.setUsername("YOUR USERNAME");
        basicAuth.setPassword("YOUR PASSWORD");

        PersistenceApi apiInstance = new PersistenceApi();
        Query query = new Query(); // Query | Query defined with criteria
        try {
            Integer result = apiInstance.countPost(query);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling PersistenceApi#countPost");
            e.printStackTrace();
        }
    }
}

```

## Documentation for API Endpoints

All URIs are relative to *http://onyxdevtools.com:8082/onyx*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*PersistenceApi* | [**countPost**](docs/PersistenceApi.md#countPost) | **POST** /count | Get count for query
*PersistenceApi* | [**deleteEntitiesPost**](docs/PersistenceApi.md#deleteEntitiesPost) | **POST** /deleteEntities | Bulk Delete Managed Entities
*PersistenceApi* | [**deleteEntityPost**](docs/PersistenceApi.md#deleteEntityPost) | **POST** /deleteEntity | Delete Managed Entity
*PersistenceApi* | [**executeDeletePost**](docs/PersistenceApi.md#executeDeletePost) | **POST** /executeDelete | Execute Delete Query
*PersistenceApi* | [**executeQueryPost**](docs/PersistenceApi.md#executeQueryPost) | **POST** /executeQuery | Execute Query
*PersistenceApi* | [**executeUpdatePost**](docs/PersistenceApi.md#executeUpdatePost) | **POST** /executeUpdate | Execute Update Query
*PersistenceApi* | [**existsWithIdPost**](docs/PersistenceApi.md#existsWithIdPost) | **POST** /existsWithId | Entity exists
*PersistenceApi* | [**findByIdPost**](docs/PersistenceApi.md#findByIdPost) | **POST** /findById | Find Managed Entity by Primary Key
*PersistenceApi* | [**initializePost**](docs/PersistenceApi.md#initializePost) | **POST** /initialize | Initialize Managed Entity&#39;s relationship by attribute name
*PersistenceApi* | [**saveEntitiesPost**](docs/PersistenceApi.md#saveEntitiesPost) | **POST** /saveEntities | Bulk Save Managed Entities
*PersistenceApi* | [**saveEntityPost**](docs/PersistenceApi.md#saveEntityPost) | **POST** /saveEntity | Save Managed Entity
*PersistenceApi* | [**saveRelationshipsPost**](docs/PersistenceApi.md#saveRelationshipsPost) | **POST** /saveRelationships | Bulk Save Relationships


## Documentation for Models

 - [AttributeUpdate](docs/AttributeUpdate.md)
 - [DeleteEntitiesRequest](docs/DeleteEntitiesRequest.md)
 - [DeleteEntityRequest](docs/DeleteEntityRequest.md)
 - [FindRequest](docs/FindRequest.md)
 - [InitializeRequest](docs/InitializeRequest.md)
 - [ManagedEntity](docs/ManagedEntity.md)
 - [NoResultsException](docs/NoResultsException.md)
 - [OnyxException](docs/OnyxException.md)
 - [Query](docs/Query.md)
 - [QueryCriteria](docs/QueryCriteria.md)
 - [QueryCriteriaOperator](docs/QueryCriteriaOperator.md)
 - [QueryOrder](docs/QueryOrder.md)
 - [QueryResponse](docs/QueryResponse.md)
 - [SaveEntitiesRequest](docs/SaveEntitiesRequest.md)
 - [SaveEntityRequest](docs/SaveEntityRequest.md)
 - [SaveRelationshipRequest](docs/SaveRelationshipRequest.md)


## Documentation for Authorization

Authentication schemes defined for the API:
### basicAuth

- **Type**: HTTP basic authentication


## Recommendation

It's recommended to create an instance of `ApiClient` per thread in a multithreaded environment to avoid any potential issues.

## Author



