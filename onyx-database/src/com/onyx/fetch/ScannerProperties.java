package com.onyx.fetch;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityException;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.record.RecordController;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 2/11/15.
 */
public class ScannerProperties
{
    public EntityDescriptor descriptor = null;
    public RecordController recordController = null;
    public AttributeDescriptor attributeDescriptor = null;
    public boolean useParentDescriptor = true;
    protected Query query;
    protected SchemaContext context;

    public ScannerProperties(EntityDescriptor descriptor, AttributeDescriptor attributeDescriptor, boolean useParentDescriptor, SchemaContext context)
    {
        this.context = context;
        this.descriptor = descriptor;
        this.attributeDescriptor = attributeDescriptor;
        this.recordController = context.getRecordController(descriptor);
        this.useParentDescriptor = useParentDescriptor;
    }

    /**
     * Get scanner properties for certain attributes
     * @param attributes
     * @return
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws com.onyx.exception.EntityException
     */
    public static List<ScannerProperties> getScannerProperties(String[] attributes, EntityDescriptor descriptor, Query query, SchemaContext context) throws EntityException
    {
        // Get the attribute names so that we can hydrate them later on
        final List<ScannerProperties> scanObjects = new ArrayList<>();

        String[] attributeTokens = null; // Contains the . notation attribute name or relationship designation say for instance child.sampleAttribute.id

        String relationshipsAttributeName; // Get the last in the list so we know what the actual relationship's attribute name is

        EntityDescriptor previousDescriptor; // This is so we can keep track of what the final descriptor is in the DOM
        RelationshipDescriptor relationshipDescriptor;
        Object tmpObject = null; // Temporary object instantiated so that we can gather descriptor information


        // We need to go through the attributes and get the correct serializer and attribute that we need to scan
        // The information is then stored in the ScanObject class name.  That is a utility class to keep a handle on
        // what we are looking for and what file we are looking in for the object type
        for (String attribute : attributes)
        {
            attributeTokens = attribute.split("\\.");

            if (attributeTokens.length > 1)
            {
                relationshipsAttributeName = attributeTokens[attributeTokens.length - 1]; // Get the last in the list so we know what the actual relationship's attribute name is

                previousDescriptor = descriptor; // This is so we can keep track of what the final descriptor is in the DOM

                for (int p = 0; p < attributeTokens.length - 1; p++) // -2 because we ignore the last
                {
                    final String token = attributeTokens[p];
                    relationshipDescriptor = previousDescriptor.getRelationships().get(token);
                    tmpObject = EntityDescriptor.createNewEntity(relationshipDescriptor.getInverseClass()); // Keep on getting the descriptors until we get what we need
                    previousDescriptor = context.getDescriptorForEntity(tmpObject, query.getPartition());
                }

                // Hey we found what we want, lets get the attribute and than decide what descriptor we got
                final AttributeDescriptor attributeDescriptor = previousDescriptor.getAttributes().get(relationshipsAttributeName);
                scanObjects.add(new ScannerProperties(previousDescriptor, attributeDescriptor, false, context));
            } else
            {
                // Generic ole serializer information and add to the serializer
                final AttributeDescriptor attributeDescriptor = descriptor.getAttributes().get(attribute);
                if (attributeDescriptor == null)
                {
                    throw new AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE + ": " + attribute + " not found on entity " + descriptor.getClazz().getName());
                }
                scanObjects.add(new ScannerProperties(descriptor, attributeDescriptor, true, context));
            }
        }
        return scanObjects;
    }

}