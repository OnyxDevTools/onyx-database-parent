# PersistenceApi

All URIs are relative to *http://onyxdevtools.com:8082/onyx*

Method | HTTP request | Description
------------- | ------------- | -------------
[**countPost**](PersistenceApi.md#countPost) | **POST** /count | Get count for query
[**deleteEntitiesPost**](PersistenceApi.md#deleteEntitiesPost) | **POST** /deleteEntities | Bulk Delete Managed Entities
[**deleteEntityPost**](PersistenceApi.md#deleteEntityPost) | **POST** /deleteEntity | Delete Managed Entity
[**executeDeletePost**](PersistenceApi.md#executeDeletePost) | **POST** /executeDelete | Execute Delete Query
[**executeQueryPost**](PersistenceApi.md#executeQueryPost) | **POST** /executeQuery | Execute Query
[**executeUpdatePost**](PersistenceApi.md#executeUpdatePost) | **POST** /executeUpdate | Execute Update Query
[**existsWithIdPost**](PersistenceApi.md#existsWithIdPost) | **POST** /existsWithId | Entity exists
[**findByIdPost**](PersistenceApi.md#findByIdPost) | **POST** /findById | Find Managed Entity by Primary Key
[**initializePost**](PersistenceApi.md#initializePost) | **POST** /initialize | Initialize Managed Entity&#39;s relationship by attribute name
[**saveEntitiesPost**](PersistenceApi.md#saveEntitiesPost) | **POST** /saveEntities | Bulk Save Managed Entities
[**saveEntityPost**](PersistenceApi.md#saveEntityPost) | **POST** /saveEntity | Save Managed Entity
[**saveRelationshipsPost**](PersistenceApi.md#saveRelationshipsPost) | **POST** /saveRelationships | Bulk Save Relationships


<a name="countPost"></a>
# **countPost**
> Integer countPost(query)

Get count for query

Get the number of items matching query criteria

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

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
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **query** | [**Query**](Query.md)| Query defined with criteria |

### Return type

**Integer**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="deleteEntitiesPost"></a>
# **deleteEntitiesPost**
> deleteEntitiesPost(request)

Bulk Delete Managed Entities

This is used to batch delete entities in order to provide optimized throughput. 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
DeleteEntitiesRequest request = new DeleteEntitiesRequest(); // DeleteEntitiesRequest | Save Entities Request
try {
    apiInstance.deleteEntitiesPost(request);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#deleteEntitiesPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**DeleteEntitiesRequest**](DeleteEntitiesRequest.md)| Save Entities Request |

### Return type

null (empty response body)

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="deleteEntityPost"></a>
# **deleteEntityPost**
> Boolean deleteEntityPost(request)

Delete Managed Entity

The Delete Entity endpoint is used to persist an entity and cascade entity relationships. 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
DeleteEntityRequest request = new DeleteEntityRequest(); // DeleteEntityRequest | Managed Entity Request Object
try {
    Boolean result = apiInstance.deleteEntityPost(request);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#deleteEntityPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**DeleteEntityRequest**](DeleteEntityRequest.md)| Managed Entity Request Object |

### Return type

**Boolean**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="executeDeletePost"></a>
# **executeDeletePost**
> Integer executeDeletePost(query)

Execute Delete Query

Execute delete query with defined criteria

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
Query query = new Query(); // Query | Query defined with criteria
try {
    Integer result = apiInstance.executeDeletePost(query);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#executeDeletePost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **query** | [**Query**](Query.md)| Query defined with criteria |

### Return type

**Integer**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="executeQueryPost"></a>
# **executeQueryPost**
> QueryResponse executeQueryPost(query)

Execute Query

Execute query with defined criteria 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
Query query = new Query(); // Query | Query defined with criteria
try {
    QueryResponse result = apiInstance.executeQueryPost(query);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#executeQueryPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **query** | [**Query**](Query.md)| Query defined with criteria |

### Return type

[**QueryResponse**](QueryResponse.md)

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="executeUpdatePost"></a>
# **executeUpdatePost**
> Integer executeUpdatePost(query)

Execute Update Query

Execute update query with defined criteria and update instructions

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
Query query = new Query(); // Query | Query defined with criteria and update instructions
try {
    Integer result = apiInstance.executeUpdatePost(query);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#executeUpdatePost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **query** | [**Query**](Query.md)| Query defined with criteria and update instructions |

### Return type

**Integer**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="existsWithIdPost"></a>
# **existsWithIdPost**
> Boolean existsWithIdPost(request)

Entity exists

Find Managed Entity by primary Key within Partition(optional) and determine if it exists 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
FindRequest request = new FindRequest(); // FindRequest | Managed Entity Request Object
try {
    Boolean result = apiInstance.existsWithIdPost(request);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#existsWithIdPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**FindRequest**](FindRequest.md)| Managed Entity Request Object |

### Return type

**Boolean**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="findByIdPost"></a>
# **findByIdPost**
> Object findByIdPost(request)

Find Managed Entity by Primary Key

Find Managed Entity by primary Key within Partition(optional) 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
FindRequest request = new FindRequest(); // FindRequest | Managed Entity Request Object
try {
    Object result = apiInstance.findByIdPost(request);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#findByIdPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**FindRequest**](FindRequest.md)| Managed Entity Request Object |

### Return type

**Object**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="initializePost"></a>
# **initializePost**
> List&lt;Object&gt; initializePost(request)

Initialize Managed Entity&#39;s relationship by attribute name

Hydrate relationship associated to that entity. 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
InitializeRequest request = new InitializeRequest(); // InitializeRequest | Initilize Relationship Request
try {
    List<Object> result = apiInstance.initializePost(request);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#initializePost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**InitializeRequest**](InitializeRequest.md)| Initilize Relationship Request |

### Return type

**List&lt;Object&gt;**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="saveEntitiesPost"></a>
# **saveEntitiesPost**
> saveEntitiesPost(request)

Bulk Save Managed Entities

This is used to batch save entities in order to provide optimized throughput. 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
SaveEntitiesRequest request = new SaveEntitiesRequest(); // SaveEntitiesRequest | Save Entities Request
try {
    apiInstance.saveEntitiesPost(request);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#saveEntitiesPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**SaveEntitiesRequest**](SaveEntitiesRequest.md)| Save Entities Request |

### Return type

null (empty response body)

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="saveEntityPost"></a>
# **saveEntityPost**
> Object saveEntityPost(request)

Save Managed Entity

The Save Entity endpoint is used to persist an entity and cascade entity relationships. 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
SaveEntityRequest request = new SaveEntityRequest(); // SaveEntityRequest | Managed Entity Request Object
try {
    Object result = apiInstance.saveEntityPost(request);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#saveEntityPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**SaveEntityRequest**](SaveEntityRequest.md)| Managed Entity Request Object |

### Return type

**Object**

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

<a name="saveRelationshipsPost"></a>
# **saveRelationshipsPost**
> saveRelationshipsPost(request)

Bulk Save Relationships

This is used to batch save an entity&#39;s relationship in order to offer more throughput than persisting. 

### Example
```java
// Import classes:
//import io.swagger.client.ApiClient;
//import io.swagger.client.ApiException;
//import io.swagger.client.Configuration;
//import io.swagger.client.auth.*;
//import io.swagger.client.api.PersistenceApi;

ApiClient defaultClient = Configuration.getDefaultApiClient();

// Configure HTTP basic authorization: basicAuth
HttpBasicAuth basicAuth = (HttpBasicAuth) defaultClient.getAuthentication("basicAuth");
basicAuth.setUsername("YOUR USERNAME");
basicAuth.setPassword("YOUR PASSWORD");

PersistenceApi apiInstance = new PersistenceApi();
SaveRelationshipRequest request = new SaveRelationshipRequest(); // SaveRelationshipRequest | Save Relationships Request
try {
    apiInstance.saveRelationshipsPost(request);
} catch (ApiException e) {
    System.err.println("Exception when calling PersistenceApi#saveRelationshipsPost");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **request** | [**SaveRelationshipRequest**](SaveRelationshipRequest.md)| Save Relationships Request |

### Return type

null (empty response body)

### Authorization

[basicAuth](../README.md#basicAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

