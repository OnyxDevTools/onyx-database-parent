package com.onyx.query;

import java.util.Map;


/**
 * Created by tosborn1 on 3/21/17.
 *
 * This method denotes a cached query containing its references and potentially its values
 *
 * @since 1.3.0 When query caching was implemented
 */
public class CachedResults {

    // Query reference/values
    private final Map references;

    /**
     * Constructor containing query resutls
     * @param results The references of a query
     *
     *                Prerequisite, if this is a sorted query, you must send in a TreeMap so that
     *                the query order is retained.
     * @since 1.3.0
     */
    public CachedResults(Map results)
    {
        this.references = results;
    }

    /**
     * Get the references of a query
     * @return Entity references
     *
     * @since 1.3.0
     */
    public Map getReferences() {
        return references;
    }

}
