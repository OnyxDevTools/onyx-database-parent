package com.onyx.descriptor;

import com.onyx.util.OffsetField;

/**
 * Created by timothy.osborn on 12/12/14.
 */
public abstract class AbstractBaseDescriptor
{

    protected String name;
    protected Class type;
    protected OffsetField partitionField = null;

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

    public OffsetField getPartitionField()
    {
        return partitionField;
    }

    public void setPartitionField(OffsetField partitionField)
    {
        this.partitionField = partitionField;
    }
}
