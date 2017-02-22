
package com.onyxdevtools.persist.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

import java.util.Date;

@Entity
@SuppressWarnings("unused")
public class Person extends ManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    private Integer id;

    @Attribute(nullable=false)
    private Date dateCreated;

    @Attribute
    private Date dateUpdated;

    @Attribute(nullable=false)
    private String firstName;

    @Attribute(nullable=false)
    private String lastName;

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