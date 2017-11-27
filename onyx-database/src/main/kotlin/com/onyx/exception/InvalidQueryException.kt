package com.onyx.exception

/**
 * Created by Tim Osborn on 3/14/17.
 *
 *
 * This exception indicates an issue with the query detected during runtime.
 */
class InvalidQueryException : OnyxException(RELATIONSHIP_PARTITION_ALL_EXCEPTION) {
    companion object {
        private val RELATIONSHIP_PARTITION_ALL_EXCEPTION = "Invalid Query Predicates.  When applying relationship query predicates you cannot specify QueryPartitionMode.ALL"
    }
}
