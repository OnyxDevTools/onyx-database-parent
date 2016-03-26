package com.onyx.descriptor;

import com.onyx.persistence.annotations.IdentifierGenerator;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class IdentifierDescriptor extends IndexDescriptor
{
    protected IdentifierGenerator generator;

    public IdentifierGenerator getGenerator()
    {
        return generator;
    }

    public void setGenerator(IdentifierGenerator generator)
    {
        this.generator = generator;
    }

}
