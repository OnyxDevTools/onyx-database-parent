package com.onyxdevtools.embedded.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

import java.util.Date;

@Entity
@SuppressWarnings("unused")
public class Person extends ManagedEntity
{

    @Identifier
    @Attribute
    private String id;

    @Attribute
    private Date dateCreated;

    @Attribute
    private Date dateUpdated;

    @Attribute
    private String firstName;

    @Attribute
    private String lastName;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
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