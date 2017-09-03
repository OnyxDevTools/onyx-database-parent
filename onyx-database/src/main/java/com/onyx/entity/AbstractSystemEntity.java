package com.onyx.entity;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.PreInsert;
import com.onyx.persistence.annotations.PreUpdate;

import java.util.Date;

/**
 * Created by timothy.osborn on 3/15/15.
 *
 * Contains date modified information
 */
class AbstractSystemEntity extends ManagedEntity
{

    @Attribute
    private Date dateUpdated;

    @Attribute
    private Date dateCreated;

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

    @PreInsert
    @SuppressWarnings("unused")
    protected void onPrePersist()
    {
        dateCreated = new Date();
    }

    @PreUpdate
    @SuppressWarnings("unused")
    protected void onPreUpdate()
    {
        dateUpdated = new Date();
    }
}
