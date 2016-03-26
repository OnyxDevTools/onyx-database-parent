package com.onyx.entity;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.PreInsert;
import com.onyx.persistence.annotations.PreUpdate;

import java.util.Date;

/**
 * Created by timothy.osborn on 3/15/15.
 */
public class AbstractSystemEntity extends ManagedEntity
{

    @Attribute
    protected Date dateUpdated;

    @Attribute
    protected Date dateCreated;

    public Date getDateUpdated()
    {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated)
    {
        this.dateUpdated = dateUpdated;
    }

    public Date getDateCreated()
    {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated)
    {
        this.dateCreated = dateCreated;
    }

    @PreInsert
    protected void onPrePersist()
    {
        dateCreated = new Date();
    }

    @PreUpdate
    protected void onPreUpdate()
    {
        dateUpdated = new Date();
    }
}
