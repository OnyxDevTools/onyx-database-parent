package com.onyx.descriptor;

import com.onyx.exception.EntityTypeMatchException;
import com.onyx.util.AttributeField;

import java.util.Date;

/**
 * Created by timothy.osborn on 12/12/14.
 */
public abstract class AbstractBaseDescriptor
{

    protected String name;
    protected Class type;
    protected AttributeField partitionField = null;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Class getType()
    {
        return type;
    }

    public void setType(Class type)
    {
        this.type = type;
    }

    public AttributeField getPartitionField()
    {
        return partitionField;
    }

    public void setPartitionField(AttributeField partitionField)
    {
        this.partitionField = partitionField;
    }
}
