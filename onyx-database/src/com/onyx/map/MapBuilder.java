package com.onyx.map;

import com.onyx.map.node.Header;
import com.onyx.map.serializer.Serializers;

import java.util.Map;

/**
 * Created by tosborn1 on 7/30/15.
 */
public interface MapBuilder
{
    /**
     * Method get returns an instance of a hashmap
     *
     * @param name
     * @return
     */
    Map getHashMap(String name);

    /**
     * Only update the first position for a header
     *
     * @param header
     * @param next
     */
    void updateHeaderNext(Header header, long next);

    /**
     * Close Map Builder.  Flush the file writes
     */
    void close();

    /**
     * Commit Map Builder file synchronize file writes
     */
    void commit();

    /**
     * Delete file
     */
    void delete();

    /**
     * Getter for serializers
     *
     * @return
     */
     Serializers getSerializers();
}
