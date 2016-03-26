package com.onyx.transaction;

import com.onyx.persistence.IManagedEntity;

/**
 * Created by tosborn1 on 3/25/16.
 */
public class DeleteTransaction implements Transaction
{
    public IManagedEntity entity;

    public DeleteTransaction()
    {

    }

    public DeleteTransaction(IManagedEntity entity)
    {
        this.entity = entity;
    }

    public IManagedEntity getEntity() {
        return entity;
    }

    public void setEntity(IManagedEntity entity) {
        this.entity = entity;
    }
}
