package com.onyx.descriptor;

import com.onyx.persistence.annotations.IdentifierGenerator;

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * General information about a key identifier for an entity
 */
public class IdentifierDescriptor extends IndexDescriptor
{
    @SuppressWarnings("WeakerAccess")
    protected IdentifierGenerator generator;

    public IdentifierGenerator getGenerator()
    {
        return generator;
    }

    public void setGenerator(IdentifierGenerator generator)
    {
        this.generator = generator;
    }

    @SuppressWarnings("WeakerAccess")
    protected byte loadFactor = 1;

    /**
     * This method is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * @param loadFactor Value from 1-10.
     */
    public void setLoadFactor(byte loadFactor)
    {
        this.loadFactor = loadFactor;
    }

    /**
     * Getter for the load factor
     * @return The values are from 1-10.
     *
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     */
    public byte getLoadFactor()
    {
        return this.loadFactor;
    }
}
