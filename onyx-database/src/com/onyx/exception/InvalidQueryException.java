package com.onyx.exception;

/**
 * Created by tosborn1 on 3/14/17.
 * <p>
 * This exception indicates an issue with the query detected during runtime.
 */
public class InvalidQueryException extends EntityException {
    private static final String RELATIONSHIP_PARTITION_ALL_EXCEPTION = "Invalid Query Predicates.  When applying relationship query predicates you cannot specify QueryPartitionMode.ALL";

    public InvalidQueryException() {
        super(RELATIONSHIP_PARTITION_ALL_EXCEPTION);
    }
}
