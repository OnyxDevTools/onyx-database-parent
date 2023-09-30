package com.onyx.exception

class MaxCardinalityExceededException @JvmOverloads constructor(val maxCardinality: Int, message: String? = "Your query has exceeded the maximum number of records in a result set.  Please optimize your query.  Your maximum cardinality is currently set at $maxCardinality.  This can be adjusted in configuring your PersistenceManagerFactory.  Or explore the stream method for use with big data.") : OnyxException(message)
