package com.onyx.descriptor;

import com.onyx.util.OffsetField;

/**
 * Created by timothy.osborn on 12/12/14.
 *
 * This is a base descriptor for an attribute.  It defines the properties based on annotation scanning
 */
public abstract class AbstractBaseDescriptor
{

    protected String name;
    protected Class type;
    private OffsetField partitionField = null;

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

    void setPartitionField(OffsetField partitionField)
    {
        this.partitionField = partitionField;
    }
}
