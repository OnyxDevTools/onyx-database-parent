package com.onyx.interactors.transaction.data

import com.onyx.persistence.query.Query

/**
 * Created by tosborn1 on 3/25/16.
 *
 * Update query transaction
 */
data class UpdateQueryTransaction(var query: Query) : Transaction