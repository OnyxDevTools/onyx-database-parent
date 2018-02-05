package com.onyx.interactors.scanner.impl

import com.onyx.persistence.query.QueryCriteriaOperator

interface RangeScanner {

    var isBetween:Boolean
    var rangeFrom:Any?
    var rangeTo:Any?
    var fromOperator:QueryCriteriaOperator?
    var toOperator:QueryCriteriaOperator?

}