package com.onyx.interactors.query

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.query.impl.collectors.*
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query

/**
 * This is a factory that decides what class to use to collect the results.  It encapsulates
 * the logic of determining the collector instance
 */
object QueryCollectorFactory {

    @Suppress("UNCHECKED_CAST")
    fun <T> create(context:SchemaContext, descriptor: EntityDescriptor, query:Query):QueryCollector<T> =
        if(query.isUpdateOrDelete) {
            UpdateQueryCollector(query, context, descriptor) as QueryCollector<T>
        }
        else if(query.groupBy?.isEmpty() != false
                && query.selections?.isEmpty() != false
                && query.functions().isEmpty()) {
            DefaultQueryCollector(query, context, descriptor) as QueryCollector<T>
        }
        else if(query.groupBy?.isEmpty() != false
                && query.selections?.isEmpty() != true
                && query.functions().firstOrNull { it.type.isGroupFunction } == null) {
            BasicSelectionQueryCollector(query, context, descriptor) as QueryCollector<T>
        }
        else if(query.groupBy?.isEmpty() != false
                && query.selections?.isEmpty() != true
                && query.functions().isEmpty()) {
            BasicSelectionQueryCollector(query, context, descriptor) as QueryCollector<T>
        }
        else if(query.groupBy?.isEmpty() != false
                && query.functions().isNotEmpty()
                && query.functions().firstOrNull { it.type.isGroupFunction } != null) {
            FlatFunctionSelectionQueryCollector(query, context, descriptor) as QueryCollector<T>
        }
        else if(query.groupBy?.isNotEmpty() == true
                && query.functions().isNotEmpty()
                && query.functions().firstOrNull { it.type.isGroupFunction } != null ) {
            GroupFunctionQueryCollector(query, context, descriptor) as QueryCollector<T>
        }
        else if(query.groupBy?.isNotEmpty() == true
                && query.functions().firstOrNull { it.type.isGroupFunction } == null ) {
            GroupSelectionQueryCollector(query, context, descriptor) as QueryCollector<T>
        }
        else {
            throw Exception("Query Collector undefined")
        }

    }
