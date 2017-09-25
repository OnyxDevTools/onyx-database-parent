package com.onyx.interactors.query.data

import com.onyx.descriptor.AttributeDescriptor
import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.RelationshipDescriptor
import com.onyx.exception.AttributeMissingException
import com.onyx.exception.OnyxException
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.util.ReflectionUtil

import java.util.ArrayList

/**
 * Created by timothy.osborn on 2/11/15.
 *
 * Properties to use to scan an entity
 */
class QueryAttributeResource private constructor(

    val descriptor: EntityDescriptor,
    val attributeDescriptor: AttributeDescriptor? = null,
    val relationshipDescriptor: RelationshipDescriptor? = null,
    val attribute: String, context: SchemaContext) {
    val contextId = context.contextId

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
            var relationshipsAttributeName: String // Get the last in the list so we know what the actual relationship's attribute name is
            var previousDescriptor: EntityDescriptor // This is so we can keep track of what the final descriptor is in the DOM
            var relationshipDescriptor: RelationshipDescriptor? = null
            var tmpObject: Any // Temporary object instantiated so that we can gather descriptor information

            // We need to go through the attributes and get the correct serializer and attribute that we need to scan
            // The information is then stored in the ScanObject class name.  That is a utility class to keep a handle on
            // what we are looking for and what file we are looking in for the object type
            for (attribute in attributes) {
                attributeTokens = attribute.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                if (attributeTokens.size > 1) {
                    relationshipsAttributeName = attributeTokens[attributeTokens.size - 1] // Get the last in the list so we know what the actual relationship's attribute name is

                    previousDescriptor = descriptor // This is so we can keep track of what the final descriptor is in the DOM

                    // -2 because we ignore the last
                    for (p in 0 until attributeTokens.size - 1) {
                        val token = attributeTokens[p]
                        relationshipDescriptor = previousDescriptor.relationships[token]
                        tmpObject = ReflectionUtil.createNewEntity(relationshipDescriptor!!.inverseClass) // Keep on getting the descriptors until we get what we need
                        previousDescriptor = context.getDescriptorForEntity(tmpObject, query.partition)
                    }

                    // Hey we found what we want, lets get the attribute and than decide what descriptor we got
                    val attributeDescriptor = previousDescriptor.attributes[relationshipsAttributeName]
                    scanObjects.add(QueryAttributeResource(descriptor = previousDescriptor, attributeDescriptor = attributeDescriptor, relationshipDescriptor = relationshipDescriptor, context = context, attribute = attribute))
                } else {
                    val attributeDescriptor = descriptor.attributes[attribute]
                    if (attributeDescriptor == null) {
                        relationshipDescriptor = descriptor.relationships[attribute]
                        if (relationshipDescriptor == null) {
                            throw AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE + ": " + attribute + " not found on entity " + descriptor.entityClass.name)
                        } else {
                            tmpObject = ReflectionUtil.createNewEntity(relationshipDescriptor.inverseClass) // Keep on getting the descriptors until we get what we need
                            previousDescriptor = context.getDescriptorForEntity(tmpObject, query.partition)

                            scanObjects.add(QueryAttributeResource(descriptor = previousDescriptor, relationshipDescriptor = relationshipDescriptor, context = context, attribute = attribute))
                        }
                    } else {
                        scanObjects.add(QueryAttributeResource(descriptor = descriptor, attributeDescriptor = attributeDescriptor, context = context, attribute = attribute))
                    }
                }
            }
            return scanObjects
        }
    }
}