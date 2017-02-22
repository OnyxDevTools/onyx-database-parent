package com.onyx.persistence;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.util.ReflectionUtil;

import java.io.IOException;
import java.util.Map;

/**
 * All managed entities should extend this class
 * Base class is needed for proper serialization
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.IManagedEntity
 * @since 1.0.0
 */
public abstract class ManagedEntity implements IManagedEntity, ObjectSerializable {

    private transient EntityDescriptor descriptor = null;

    public transient boolean ignoreListeners = false;

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        try {
            if (descriptor == null) {
                descriptor = buffer.serializers.context.getDescriptorForEntity(this, "");
            }

            descriptor.getAttributes().values().forEach(attribute ->
            {
                try {
                    final Object obj = ReflectionUtil.getAny(this, attribute.field);
                    buffer.writeObject(obj);
                } catch (Exception ignore) {
                }
            });
        } catch (EntityException ignore) {
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
                } catch (EntityException ignore) {}
            }

            descriptor.getAttributes().values().forEach(attribute ->
            {
                try {
                    ReflectionUtil.setAny(this, buffer.readObject(), attribute.field);
                } catch (Exception ignore) {
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
                        attribute.field = ReflectionUtil.getOffsetField(this.getClass(), attribute.getName());

                    ReflectionUtil.setAny(this, obj, attribute.field);
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * This method maps the keys from a structure to the attributes of the entity
     * @param mapObj Map to convert from
     */
    @SuppressWarnings("unused")
    public void fromMap(Map<String, Object> mapObj, SchemaContext context)
    {
        try {

            if (descriptor == null) {
                try {
                    descriptor = context.getDescriptorForEntity(this, "");
                } catch (EntityException ignore) {}
            }

            descriptor.getAttributes().values().forEach(attribute ->
            {
                try {
                    if (mapObj.containsKey(attribute.field.field.getName())) {
                        Object attributeValueWithinMap = mapObj.get(attribute.field.field.getName());
                        ReflectionUtil.setAny(this, attributeValueWithinMap, attribute.field);
                    }
                } catch (Exception ignore) {
                }
            });
        }
        catch (Exception ignore){}
    }

}
