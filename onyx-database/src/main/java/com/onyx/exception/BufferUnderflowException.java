package com.onyx.exception;

/**
 * Created by tosborn1 on 2/10/17.
 *
 */
public class BufferUnderflowException extends BufferingException {

    public static final String BUFFER_UNDERFLOW = "Buffer Underflow exception ";

    public BufferUnderflowException(@SuppressWarnings("SameParameterValue") String message) {
        super(message);
    }

    public BufferUnderflowException(@SuppressWarnings("SameParameterValue") String message, Class clazz) {
        super(message, clazz);
    }
}
