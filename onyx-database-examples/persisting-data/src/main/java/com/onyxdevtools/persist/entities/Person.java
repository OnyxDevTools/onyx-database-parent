
package com.onyxdevtools.persist.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.Date;

@Entity
public class Person extends ManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public Integer id;

    @Attribute(nullable=false)
    public Date dateCreated;

    @Attribute
    public Date dateUpdated;

    @Attribute(nullable=false)
    public String firstName;

    @Attribute(nullable=false)
    public String lastName;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Date getDateCreated()
    {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated)
    {
        this.dateCreated = dateCreated;
    }

    public Date getDateUpdated()
    {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated)
    {
        this.dateUpdated = dateUpdated;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

}