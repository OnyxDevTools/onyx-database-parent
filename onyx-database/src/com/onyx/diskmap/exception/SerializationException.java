package com.onyx.diskmap.exception;

import java.io.IOException;

/**
 * Created by timothy.osborn on 4/2/15.
 */
public class SerializationException extends IOException
{
    public static final String CHECKSUM = "Invalid serialization checksum";

    /**
     * Constructor
     *
     */
    public SerializationException()
    {
        super(CHECKSUM);
    }
}
