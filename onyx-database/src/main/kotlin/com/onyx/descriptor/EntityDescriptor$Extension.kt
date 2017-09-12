package com.onyx.descriptor

import com.onyx.interactors.record.RecordInteractor

fun EntityDescriptor.recordInteractor(): RecordInteractor = this.context.getRecordInteractor(this)