package com.onyx.transaction;

import com.onyx.persistence.IManagedEntity;

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Save entity transaction
 */
public class SaveTransaction implements Transaction{
    public IManagedEntity entity;

    @SuppressWarnings("unused")
    public SaveTransaction()
    {

    }

    public SaveTransaction(IManagedEntity entity)
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
