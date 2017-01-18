package com.onyx.structure.serializer;

/**
 * Created by timothy.osborn on 3/25/15.
 */
/**
 * Object Types
 *
 */
public enum ObjectType
{
    NULL((byte)1),
    LONG((byte)2),
    INT((byte)3),
    SHORT((byte)4),
    DOUBLE((byte) 5),
    FLOAT((byte) 6),
    BOOLEAN((byte) 7),
    STRING((byte) 8),
    BUFFER_OBJ((byte) 9),
    BYTES((byte) 10),
    HASH_SET((byte) 11),
    COLLECTION((byte) 12),
    MAP((byte) 13),
    DATE((byte) 14),
    OTHER((byte) 15),
    NODE((byte) 16),
    RECORD_REFERENCE((byte) 17),
    RECORD((byte) 18),
    ENUM((byte) 19),
    ARRAY((byte) 20),
    CHAR((byte) 21),
    BYTE((byte) 22),
    CLASS((byte) 23),
    FLOATS((byte) 24),
    SHORTS((byte) 25),
    BOOLEANS((byte) 26),
    DOUBLES((byte) 27),
    INTS((byte) 28),
    LONGS((byte) 29),
    CHARS((byte) 30);

    private byte type;

    ObjectType(byte type)
    {
        this.type = type;
    }

    public byte getType()
    {
        return type;
    }
}