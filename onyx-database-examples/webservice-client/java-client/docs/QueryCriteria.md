
# QueryCriteria

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**attribute** | **String** | Attribute to check criteria for |  [optional]
**operator** | [**OperatorEnum**](#OperatorEnum) | Query criteria operator e.x. EQUAL, NOT_EQUAL, etc... |  [optional]
**value** | **Object** | Criteria value to compare |  [optional]
**subCriteria** | [**List&lt;QueryCriteria&gt;**](QueryCriteria.md) | Sub Criteria contains and / or criteria |  [optional]
**isAnd** | **Boolean** | Is an And criteria |  [optional]
**isOr** | **Boolean** | Is an Or criteria |  [optional]
**isNot** | **Boolean** | Inverse of defined criteria |  [optional]


<a name="OperatorEnum"></a>
## Enum: OperatorEnum
Name | Value
---- | -----
EQUAL | &quot;EQUAL&quot;
NOT_EQUAL | &quot;NOT_EQUAL&quot;
NOT_STARTS_WITH | &quot;NOT_STARTS_WITH&quot;
NOT_NULL | &quot;NOT_NULL&quot;
IS_NULL | &quot;IS_NULL&quot;
STARTS_WITH | &quot;STARTS_WITH&quot;
CONTAINS | &quot;CONTAINS&quot;
NOT_CONTAINS | &quot;NOT_CONTAINS&quot;
LIKE | &quot;LIKE&quot;
NOT_LIKE | &quot;NOT_LIKE&quot;
MATCHES | &quot;MATCHES&quot;
NOT_MATCHES | &quot;NOT_MATCHES&quot;
LESS_THAN | &quot;LESS_THAN&quot;
GREATER_THAN | &quot;GREATER_THAN&quot;
LESS_THAN_EQUAL | &quot;LESS_THAN_EQUAL&quot;
GREATER_THAN_EQUAL | &quot;GREATER_THAN_EQUAL&quot;
IN | &quot;IN&quot;
NOT_IN | &quot;NOT_IN&quot;



