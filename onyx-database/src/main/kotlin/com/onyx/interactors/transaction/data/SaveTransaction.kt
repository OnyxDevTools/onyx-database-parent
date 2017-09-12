package com.onyx.interactors.transaction.data

import com.onyx.persistence.IManagedEntity

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Save entity transaction
 */
data class SaveTransaction(val entity: IManagedEntity) : Transaction
