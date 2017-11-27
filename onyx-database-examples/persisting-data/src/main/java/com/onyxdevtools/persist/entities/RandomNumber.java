
package com.onyxdevtools.persist.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.values.IdentifierGenerator;

@Entity
@SuppressWarnings("unused")
public class RandomNumber extends ManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    private Integer id;

    @Attribute(nullable=false)
    private int number;

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