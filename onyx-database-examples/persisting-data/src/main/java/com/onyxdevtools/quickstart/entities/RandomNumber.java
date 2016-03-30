
package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

@Entity
public class RandomNumber extends ManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public Integer id;

    @Attribute(nullable=false)
    public int number;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public int getNumber()
    {
        return number;
    }

    public void setNumber(int number)
    {
        this.number = number;
    }
    
    

}