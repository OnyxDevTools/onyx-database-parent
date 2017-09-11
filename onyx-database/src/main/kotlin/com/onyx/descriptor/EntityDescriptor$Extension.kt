package com.onyx.descriptor

import com.onyx.record.RecordController

fun EntityDescriptor.recordController():RecordController = this.context.getRecordController(this)