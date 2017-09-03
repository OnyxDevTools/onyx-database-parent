package com.onyx.client.exception;

/**
 * Created by tosborn1 on 7/1/16.
 *
 * The purpose of this is to represent an exception during the serialization of the packet stage
 */
public class SerializationException extends OnyxServerException {

    private static final String SERIALIZATION_EXCEPTION = "Exception occurred while serializing packet";

    /**
     * Default constructor with root cause and original request token
     * @param cause root cause
     */
    public SerializationException(Throwable cause)
    {
        super(SERIALIZATION_EXCEPTION, cause);
    }
}
