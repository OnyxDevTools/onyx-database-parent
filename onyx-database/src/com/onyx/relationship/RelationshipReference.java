package com.onyx.relationship;

import com.onyx.exception.InvalidDataTypeForOperator;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;
import com.onyx.util.CompareUtil;

import java.io.IOException;

/**
 * Created by timothy.osborn on 3/19/15.
 */
public class RelationshipReference implements ObjectSerializable, Comparable
{

    public RelationshipReference()
    {

    }

    public RelationshipReference(Object identifier, long partitionId)
    {
        this.identifier = identifier;
        this.partitionId = partitionId;
    }

    public Object identifier;
    public long partitionId;

    @Override
    public int hashCode()
    {
        if(identifier == null)
        {
            return super.hashCode();
        }
        return identifier.hashCode() + new Long(partitionId).hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if(obj instanceof RelationshipReference)
        {
            final RelationshipReference comp = (RelationshipReference) obj;
            if(comp.identifier.getClass() != identifier.getClass())
                return false;
            try
            {
                return (comp.partitionId == partitionId && CompareUtil.compare(comp.identifier, identifier, QueryCriteriaOperator.EQUAL));
            }catch (InvalidDataTypeForOperator e)
            {
                return false;
            }
        }
        return false;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeLong(partitionId);
        buffer.writeObject(identifier);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        partitionId = buffer.readLong();
        identifier = buffer.readObject();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        this.readObject(buffer, position);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }

    @Override
    public String toString()
    {
        return "Identifier " + identifier.toString() + " Partition ID " + partitionId;
    }

    @Override
    public int compareTo(Object o) {

        if(o instanceof RelationshipReference)
        {
            if(this.partitionId < ((RelationshipReference) o).partitionId)
                return -1;
            else if(this.partitionId > ((RelationshipReference) o).partitionId)
                return 1;

            if(((RelationshipReference) o).identifier.getClass() == this.identifier.getClass()
                && this.identifier instanceof Comparable)
                return ((Comparable) this.identifier).compareTo(((RelationshipReference) o).identifier);

            if(this.hashCode() < o.hashCode())
                return -1;
            else if(this.hashCode() > o.hashCode())
                return 1;
            else
                return 0;
        }
        
        return new Integer(this.hashCode()).compareTo(o.hashCode());
    }
}