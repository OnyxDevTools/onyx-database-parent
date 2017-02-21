package com.onyx.transaction;

import com.onyx.persistence.IManagedEntity;

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Delete entity transaction
 */
public class DeleteTransaction implements Transaction
{
    public IManagedEntity entity;

    @SuppressWarnings("unused")
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
