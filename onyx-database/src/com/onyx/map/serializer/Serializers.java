package com.onyx.map.serializer;

import com.onyx.persistence.context.SchemaContext;

import java.util.Map;

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * This is a contrived list of 2 maps.  To map the serializers by both id and value
 *
 */
public class Serializers
{
    public static final short CUSTOM_SERIALIZER_ID_OFFSET = 100;

    protected Map<Short, String> mapById;

    protected Map<String, Short> mapByName;

    public SchemaContext context;

    /**
     * Constructor with no parameters
     */
    public Serializers()
    {

    }

    /**
     * Constructor
     *
     * @param mapById
     * @param mapByName
     */
    public Serializers(Map mapById, Map mapByName, SchemaContext context)
    {
        this.mapById = mapById;
        this.mapByName = mapByName;
        this.context = context;
    }

    /**
     * Get the serializer class with the short id
     *
     * @param id
     * @return
     */
    public Class getSerializerClass(short id)
    {
        try
        {
            return Class.forName(mapById.get(id));
        } catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get the serializer id with the given name
     *
     * @param name
     * @return
     */
    public Short getSerializerId(String name)
    {
        return mapByName.get(name);
    }

    /**
     * Add a new serializer by name.  The id will be auto generated
     *
     * @param name
     * @return
     */
    public synchronized short add(String name)
    {
        short nextId = getNextId();
        mapById.put(nextId, name);
        mapByName.put(name, nextId);
        return nextId;
    }

    /**
     * Private getter for creating a new id
     * @return
     */
    private synchronized short getNextId()
    {
        return (short)(mapById.size() + CUSTOM_SERIALIZER_ID_OFFSET);
    }
}
