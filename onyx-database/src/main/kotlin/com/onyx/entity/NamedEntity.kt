package com.onyx.entity

import com.onyx.persistence.IManagedEntity

interface NamedEntity : IManagedEntity {
    var name:String
}