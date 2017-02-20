package com.onyx.diskmap.serializer;

/**
 * Object Types
 *
 */
enum ObjectType
{
    NULL((byte)0),
    LONG((byte)1),
    INT((byte)2),
    SHORT((byte)3),
    DOUBLE((byte)4),
    FLOAT((byte)5),
    BOOLEAN((byte)6),
    CHAR((byte) 7),
    BYTE((byte) 8),
    STRING((byte)9),
    BUFFER_OBJ((byte)10),
    HASH_SET((byte)11),
    COLLECTION((byte) 12),
    MAP((byte) 13),
    DATE((byte) 14),
    OTHER((byte) 15),
    ENUM((byte) 16),
    ARRAY((byte) 17),
    BYTES((byte) 18),
    FLOATS((byte) 19),
    SHORTS((byte) 20),
    BOOLEANS((byte) 21),
    DOUBLES((byte) 22),
    INTS((byte) 23),
    LONGS((byte) 24),
    CHARS((byte) 25);

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