package com.onyx.map.serializer;

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
    HASH_SET((byte) 12),
    COLLECTION((byte) 13),
    MAP((byte) 14),
    DATE((byte) 15),
    OTHER((byte) 16),
    NODE((byte) 17),
    RECORD_REFERENCE((byte) 18),
    RECORD((byte) 19),
    ENUM((byte) 20),
    ARRAY((byte) 21),
    CHAR((byte) 22),
    BYTE((byte) 23),
    CLASS((byte) 24),
    LAZY_RELATIONSHIP_COLLECTION((byte) 25),
    LAZY_COLLECTION((byte) 26);

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