
# Query

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**entityType** | **String** | Entities to query.  This contains the entity type&#39;s canonical name. |  [optional]
**firstRow** | **Integer** | First index of results to return (optional) |  [optional]
**maxResults** | **Integer** | Max number of results to return (optional) |  [optional]
**selections** | **List&lt;String&gt;** | Attributes to select (optional).  If not defined, it will return the entity objects. |  [optional]
**isDistinct** | **Boolean** | Distinct results.  Only applies to selection queries. (optional) |  [optional]
**partition** | **String** | Partition to search (optional) |  [optional]
**updates** | [**List&lt;AttributeUpdate&gt;**](AttributeUpdate.md) | Attributes to update and their updated values.  This should only be defined for update queries. |  [optional]
**criteria** | [**QueryCriteria**](QueryCriteria.md) |  |  [optional]
**queryOrders** | [**List&lt;QueryOrder&gt;**](QueryOrder.md) | Query result order. (optional) |  [optional]



