package com.onyx.transaction;

import com.onyx.persistence.IManagedEntity;

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Save entity transaction
 */
public class SaveTransaction implements Transaction{
    @SuppressWarnings("WeakerAccess")
    public IManagedEntity entity;

    @SuppressWarnings("unused")
    public SaveTransaction()
    {

    }

    public SaveTransaction(IManagedEntity entity)
    {
        this.entity = entity;
    }

    @SuppressWarnings("unused")
    public IManagedEntity getEntity() {
        return entity;
    }

    @SuppressWarnings("unused")
    public void setEntity(IManagedEntity entity) {
        this.entity = entity;
    }
}
