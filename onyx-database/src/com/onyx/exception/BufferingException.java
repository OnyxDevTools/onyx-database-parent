package com.onyx.exception;

/**
 * Created by tosborn1 on 8/1/16.
 *
 * This class indicates an issue when trying to serialize and de-serialize using the buffering mechanism
 */
public class BufferingException extends EntityException {

    public static final String UNKNOWN_DESERIALIZE = "Unknown exception occurred while de-serializing ";
    public static final String UNKNOWN_SERIALIZE = "Unknown exception occurred while serializing ";
    public static final String CANNOT_INSTANTIATE = "Cannot instantiate class ";
    public static final String UNKNOWN_CLASS = "Unknown class ";
    public static final String ILLEGAL_ACCESS_EXCEPTION = "Illegal Access Exception ";

    /**
     * Default Constructor with message
     *
     * @param message Error message
     */
    @SuppressWarnings("unused")
    public BufferingException(String message)
    {
        super(message);
    }

    /**
     * Constructor with message and class attempted to expandableByteBuffer
     * @param message error message
     * @param clazz class to add to error message
     */
    @SuppressWarnings("unused")
    public BufferingException(String message, Class clazz)
    {
        super(message + ((clazz != null) ? clazz.getCanonicalName() : "null"));
    }
}

