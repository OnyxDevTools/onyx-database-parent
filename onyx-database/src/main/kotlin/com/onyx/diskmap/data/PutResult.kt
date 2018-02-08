package com.onyx.diskmap.data

/**
 * This class indicates the result.  It determines if the key
 * has been inserted or updated.  Also returns the record id
 * that is mapped to the skip list node position.
 *
 * @since 2.1.3 Performance Improvements
 */
data class PutResult(val key:Any, var isInsert:Boolean = true, var recordId:Long = -1)