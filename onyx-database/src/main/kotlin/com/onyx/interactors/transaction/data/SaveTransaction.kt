package com.onyx.interactors.transaction.data

import com.onyx.persistence.IManagedEntity

/**
 * Created by Tim Osborn on 3/25/16.
 *
 * Save entity transaction
 */
data class SaveTransaction(val entity: IManagedEntity) : Transaction
