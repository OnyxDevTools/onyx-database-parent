package com.onyx.descriptor

import com.onyx.interactors.record.RecordInteractor
import com.onyx.interactors.record.impl.DefaultRecordInteractor

fun EntityDescriptor.recordInteractor(): RecordInteractor {
    val interactor = this.context.getRecordInteractor(this)
    if(this.partition != null)
    {
        assert((interactor as DefaultRecordInteractor).entityDescriptor.partition!!.partitionValue == partition!!.partitionValue)
    }
    return interactor
}