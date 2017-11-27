package com.onyx.descriptor

import com.onyx.interactors.record.RecordInteractor

/**
 * Get the record interactor corresponding to this descriptor
 *
 * @since 2.0.0
 */
fun EntityDescriptor.recordInteractor(): RecordInteractor = this.context.getRecordInteractor(this)
