package com.onyx.interactors.transaction.data

import com.onyx.persistence.query.Query

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Delete query transaction
 */
data class DeleteQueryTransaction(val query: Query) : Transaction
