package com.onyx.persistence;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.util.AttributeField;
import com.onyx.util.ObjectUtil;

import java.io.*;
import java.util.Comparator;
import java.util.Map;

/**
 * All managed entities should extend this class
 * Base class is needed for proper serialization
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.IManagedEntity
 * @since 1.0.0
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public abstract class ManagedEntity implements IManagedEntity, ObjectSerializable {

    @JsonIgnore
    private transient EntityDescriptor descriptor = null;

    @JsonIgnore
    public transient boolean ignoreListeners = false;

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        try {
            if (descriptor == null) {
                descriptor = buffer.serializers.context.getDescriptorForEntity(this, "");
            }

            descriptor.getAttributes().values().stream().forEach(attribute ->
            {
                try {
                    final Object obj = DefaultSchemaContext.reflection.getAttribute(attribute.field, this);
                    buffer.writeObject(obj);
                } catch (Exception e) {
                }
            });
        } catch (EntityException e) {
        }
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        readObject(buffer, 0);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException {
        readObject(buffer, 0, 0);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

        // If System Entity does not exist, read by entity descriptor
        if (serializerId == 0)
        {
            if (descriptor == null) {
                try {
                    descriptor = buffer.serializers.context.getDescriptorForEntity(this, "");
                } catch (EntityException e) {}
            }

            descriptor.getAttributes().values().stream().forEach(attribute ->
            {
                try {
                    DefaultSchemaContext.reflection.setAttribute(this, buffer.readObject(), attribute.field);
                } catch (Exception e) {
                }
            });
        }
        // System Entity is used to de-serialize since we have them versioned
        else
        {
            SystemEntity systemEntity = buffer.serializers.context.getSystemEntityById(serializerId);

            for (SystemAttribute attribute : systemEntity.getAttributes()) {
                Object obj = buffer.readObject();
                try {
                    if (attribute.field == null)
                        attribute.field = new AttributeField(ObjectUtil.getField(this.getClass(), attribute.getName()));

                    DefaultSchemaContext.reflection.setAttribute(this, obj, attribute.field);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * This method maps the keys from a map to the attributes of the entity
     * @param mapObj Map to convert from
     */
    public void fromMap(Map<String, Object> mapObj, SchemaContext context)
    {
        try {

            if (descriptor == null) {
                try {
                    descriptor = context.getDescriptorForEntity(this, "");
                } catch (EntityException e) {}
            }

            descriptor.getAttributes().values().stream().forEach(attribute ->
            {
                try {
                    if(mapObj.containsKey(attribute.field.field.getName())) {
                        Object attributeValueWithinMap = mapObj.get(attribute.field.field.getName());
                        DefaultSchemaContext.reflection.setAttribute(this, attributeValueWithinMap, attribute.field);
                    }
                } catch (Exception e) {
                }
            });
        }
        catch (Exception e){}
    }
}
