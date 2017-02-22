
package com.onyxdevtools.cache.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

import java.util.Date;

@Entity
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

    @SuppressWarnings("unused")
    public String getId()
    {
        return id;
    }

    public void setId(@SuppressWarnings("SameParameterValue") String id)
    {
        this.id = id;
    }

    @SuppressWarnings("unused")
    public Date getDateCreated()
    {
        return dateCreated;
    }

    @SuppressWarnings("unused")
    public void setDateCreated(Date dateCreated)
    {
        this.dateCreated = dateCreated;
    }

    @SuppressWarnings("unused")
    public Date getDateUpdated()
    {
        return dateUpdated;
    }

    @SuppressWarnings("unused")
    public void setDateUpdated(Date dateUpdated)
    {
        this.dateUpdated = dateUpdated;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(@SuppressWarnings("SameParameterValue") String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(@SuppressWarnings("SameParameterValue") String lastName)
    {
        this.lastName = lastName;
    }

}