package com.onyx.fetch;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 3/5/15.
 */
public class PartitionReference implements Externalizable, Comparable
{
    public PartitionReference()
    {

    }
    public PartitionReference(long partition, long reference)
    {
        this.partition = partition;
        this.reference = reference;
    }

    public long reference;
    public long partition;

    @Override
    public int hashCode()
    {
        return (String.valueOf(reference) + String.valueOf(partition)).hashCode();
    }

    @Override
    public boolean equals(Object val)
    {
        if(val instanceof PartitionReference)
        {
            final PartitionReference index = (PartitionReference) val;
            return (index.partition == this.partition && index.reference == this.reference);
        }
        return false;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        out.writeLong(partition);
        out.writeLong(reference);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        partition = in.readLong();
        reference = in.readLong();
    }

    @Override
    public int compareTo(Object o) {
        if(o instanceof PartitionReference)
        {
            PartitionReference other = (PartitionReference)o;
            if(this.partition < other.partition)
                return -1;
            else if(this.partition > other.partition)
                return 1;
            else if(this.reference < other.reference)
                return -1;
            else if(this.reference > other.reference)
                return 1;
            return 0;
        }
        else
        {
            if(this.hashCode() < o.hashCode())
                return -1;
            else if(this.hashCode() > o.hashCode())
                return 1;
            return 0;
        }
    }
}
