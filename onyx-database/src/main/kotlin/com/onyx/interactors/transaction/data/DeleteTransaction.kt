package com.onyx.interactors.transaction.data

import com.onyx.persistence.IManagedEntity

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Delete entity transaction
 */
data class DeleteTransaction(val entity: IManagedEntity) : Transaction
