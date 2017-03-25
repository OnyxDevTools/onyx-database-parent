package com.onyx.query;

import com.onyx.util.map.CompatMap;

import java.util.Map;


/**
 * Created by tosborn1 on 3/21/17.
 */
public class CachedResults {

    private Map references;

    public CachedResults(Map results)
    {
        this.references = results;
    }

    public Map getReferences() {
        return references;
    }

    public void setReferences(CompatMap references) {
        this.references = references;
    }
}
