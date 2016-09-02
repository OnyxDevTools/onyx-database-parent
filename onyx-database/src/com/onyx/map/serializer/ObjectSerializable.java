package com.onyx.map.serializer;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public interface ObjectSerializable extends Serializable
{
    void writeObject(ObjectBuffer buffer) throws IOException;

    void readObject(ObjectBuffer buffer) throws IOException;

    default void readObject(ObjectBuffer buffer, long position) throws IOException{}

    default void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException{}

}
