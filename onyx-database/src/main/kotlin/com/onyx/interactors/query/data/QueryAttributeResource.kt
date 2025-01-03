package com.onyx.interactors.query.data

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.AttributeMissingException
import com.onyx.exception.OnyxException
import com.onyx.extension.createNewEntity
import com.onyx.extension.getAttributeWithinSelection
import com.onyx.extension.getFunctionWithinSelection
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.Query

import java.util.ArrayList

/**
 * Created by timothy.osborn on 2/11/15.
 *
 * Properties to use to scan an entity
 */
class QueryAttributeResource(

    val descriptor: EntityDescriptor,
    val relationshipDescriptor: RelationshipDescriptor? = null,
    val attribute: String,
    val selection: String, context: SchemaContext, val function: QueryFunction? = null
) {
    val contextId = context.contextId
    val attributeParts:List<String> by lazy { attribute.split(".") }

    companion object {

        /**
         * Get scanner properties for certain attributes.  This method will aggregate the attributes and traverse the relationship
         * descriptors to get the correct corresponding attribute and or relationship for the query.
         *
         * @param attributes Attributes to scan
         * @return List of scanner properties
         */
        @Throws(OnyxException::class)
        fun create(attributes: Array<String>, descriptor: EntityDescriptor, query: Query, context: SchemaContext): List<QueryAttributeResource> {
            // Get the attribute names so that we can hydrate them later on
            val scanObjects = ArrayList<QueryAttributeResource>()

            var attributeTokens: Array<String> // Contains the . notation attribute name or relationship designation say for instance child.sampleAttribute.id
            var previousDescriptor: EntityDescriptor // This is so we can keep track of what the final descriptor is in the DOM
            var relationshipDescriptor: RelationshipDescriptor? = null
            var tmpObject: Any // Temporary object instantiated so that we can gather descriptor information

            // We need to go through the attributes and get the correct serializer and attribute that we need to scan
            // The information is then stored in the ScanObject class name.  That is a utility class to keep a handle on
            // what we are looking for and what file we are looking in for the object type
            for (it in attributes) {
                val attribute = it.getAttributeWithinSelection()
                val function = it.getFunctionWithinSelection()

                attributeTokens = attribute.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                if (attributeTokens.size > 1) {
                    previousDescriptor = descriptor // This is so we can keep track of what the final descriptor is in the DOM

                    // -2 because we ignore the last
                    for (p in 0 until attributeTokens.size - 1) {
                        val token = attributeTokens[p]
                        relationshipDescriptor = previousDescriptor.relationships[token]
                        tmpObject = relationshipDescriptor!!.inverseClass.createNewEntity(context.contextId) // Keep on getting the descriptors until we get what we need
                        previousDescriptor = context.getDescriptorForEntity(tmpObject, query.partition)
                    }

                    // Hey we found what we want, lets get the attribute and than decide what descriptor we got
                    scanObjects.add(QueryAttributeResource(
                        descriptor = previousDescriptor,
                        relationshipDescriptor = relationshipDescriptor,
                        attribute = attribute,
                        selection = it,
                        context = context,
                        function = function
                    ))
                } else {
                    val attributeDescriptor = descriptor.attributes[attribute]
                    if (attributeDescriptor == null) {
                        relationshipDescriptor = descriptor.relationships[attribute]
                        if (relationshipDescriptor == null) {
                            throw AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE + ": " + attribute + " not found on entity " + descriptor.entityClass.name)
                        } else {
                            tmpObject = relationshipDescriptor.inverseClass.createNewEntity(context.contextId) // Keep on getting the descriptors until we get what we need
                            previousDescriptor = context.getDescriptorForEntity(tmpObject, query.partition)

                            scanObjects.add(QueryAttributeResource(
                                descriptor = previousDescriptor,
                                relationshipDescriptor = relationshipDescriptor,
                                attribute = attribute,
                                selection = it,
                                context = context,
                                function = function
                            ))
                        }
                    } else {
                        scanObjects.add(QueryAttributeResource(
                            descriptor = descriptor,
                            attribute = attribute,
                            selection = it,
                            context = context,
                            function = function
                        ))
                    }
                }
            }
            return scanObjects
        }
    }
}