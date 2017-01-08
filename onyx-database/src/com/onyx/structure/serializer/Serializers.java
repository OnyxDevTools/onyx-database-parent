package com.onyx.structure.serializer;

import com.onyx.persistence.context.SchemaContext;
import java.util.Map;

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * This is a contrived list of 2 maps.  To structure the serializers by both id and key
 *
 */
public class Serializers
{
    private static final short CUSTOM_SERIALIZER_ID_OFFSET = 100;

    private Map<Short, String> mapById;
    private Map<String, Short> mapByName;

    public SchemaContext context;

    /**
     * Constructor with no parameters
     */
    @SuppressWarnings("unused")
    public Serializers()
    {

    }

    /**
     * Constructor
     *
     * @param mapById Hash Map by serializer ID
     * @param mapByName Hash Map By Serializer Name
     * @param context Database Schema Context
     */
    public Serializers(Map<Short, String> mapById, Map<String, Short> mapByName, SchemaContext context)
    {
        this.mapById = mapById;
        this.mapByName = mapByName;
        this.context = context;
    }

    /**
     * Get the serializer class with the short id
     *
     * @param id Serializer ID
     * @return Class that corresponds to the serializer
     */
    Class getSerializerClass(short id)
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
     * @param name Serializer Name
     * @return Serializer ID
     */
    Short getSerializerId(String name)
    {
        return mapByName.get(name);
    }

    /**
     * Add a new serializer by name.  The id will be auto generated
     *
     * @param name Serializer Name
     * @return Newly Generated serializer ID
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
     * @return Nex sequential serializer id
     */
    private synchronized short getNextId()
    {
        return (short)(mapById.size() + CUSTOM_SERIALIZER_ID_OFFSET);
    }
}
