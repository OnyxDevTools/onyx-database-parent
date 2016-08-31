package com.onyx.map.store;

/**
 * Created by tosborn1 on 8/30/16.
 */
public class ReclaimedSpace implements Comparable<ReclaimedSpace>
{
    public long position;
    public int size;

    public ReclaimedSpace(long position, int size)
    {
        this.position = position;
        this.size = size;
    }

    @Override
    public int hashCode()
    {
        return new Long(position).hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if(obj != null && obj instanceof ReclaimedSpace)
        {
            return (((ReclaimedSpace) obj).position == position && ((ReclaimedSpace) obj).size == size);
        }
        return false;
    }

    @Override
    public int compareTo(ReclaimedSpace o)
    {
        return new Integer(new Integer(size)).compareTo(o.size);
    }
}