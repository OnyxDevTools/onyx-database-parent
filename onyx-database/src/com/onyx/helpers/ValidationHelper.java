package com.onyx.helpers;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IdentifierDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.*;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.IdentifierGenerator;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.update.AttributeUpdate;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 1/20/15.
 * <p>
 * Validate an entity
 */
public class ValidationHelper {

    /**
     * Validate Entity
     *
     * @param descriptor Entity descriptor
     * @param entity     Entity to validate
     * @return Whether the entity passes validation
     */
    @SuppressWarnings({"UnusedReturnValue", "SameReturnValue"})
    public static boolean validateEntity(EntityDescriptor descriptor, IManagedEntity entity) throws EntityException {

        final List<Throwable> exceptionsThrown = new ArrayList<>();

        for(AttributeDescriptor attribute : descriptor.getAttributes().values())
        {
            OffsetField field = null;
            Object value = null;
            try {

                if (!attribute.isNullable()) {
                    field = attribute.getField();
                    value = ReflectionUtil.getAny(entity, field);

                    if (value == null) {
                        throw new AttributeNonNullException(AttributeNonNullException.ATTRIBUTE_NULL_EXCEPTION, attribute.getName());
                    }
                }

                if (attribute.getType() == String.class) {
                    if (field == null) {
                        field = attribute.getField();
                        value = ReflectionUtil.getAny(entity, field);
                    }


                    if (value != null && ((String) value).length() > attribute.getSize() && attribute.getSize() > -1) {
                        throw new AttributeSizeException(AttributeSizeException.ATTRIBUTE_SIZE_EXCEPTION, attribute.getName());
                    }
                }
            } catch (Exception e) {
                exceptionsThrown.add(e);
            }
        }


        try {
            final IdentifierDescriptor identifierDescriptor = descriptor.getIdentifier();
            if (identifierDescriptor.getGenerator() == IdentifierGenerator.NONE) {
                OffsetField field = identifierDescriptor.getField();

                Object value = ReflectionUtil.getAny(entity, field);
                if (value == null) {
                    throw new IdentifierRequiredException(IdentifierRequiredException.IDENTIFIER_REQUIRED_EXCEPTION, identifierDescriptor.getName());
                }
            }
        } catch (Exception e) {
            exceptionsThrown.add(e);
        }

        if (exceptionsThrown.size() > 0) {
            Throwable t = exceptionsThrown.get(0);
            if (t instanceof EntityException) {
                throw (EntityException) t;
            } else {
                throw new EntityException(EntityException.UNKNOWN_EXCEPTION, t);
            }
        }

        return true;
    }

    /**
     * Validate Entity
     *
     * @param descriptor Entity descriptor
     * @param query      Query to validate
     * @param context    Schema context
     * @return Whether the query is valid
     */
    @SuppressWarnings({"UnusedReturnValue", "SameReturnValue"})
    public static boolean validateQuery(EntityDescriptor descriptor, Query query, SchemaContext context) throws EntityException {
        if (query.getUpdates() == null) {
            return true;
        }
        for (AttributeUpdate instruction : query.getUpdates()) {
            String fieldName = instruction.getFieldName();

            Object value = instruction.getValue();

            AttributeDescriptor attribute = descriptor.getAttributes().get(fieldName);
            IndexDescriptor indexDescriptor = descriptor.getIndexes().get(fieldName);

            instruction.setAttributeDescriptor(attribute);

            if (indexDescriptor != null) {
                instruction.setIndexController(context.getIndexController(indexDescriptor));
            }

            if (attribute == null) {
                throw new AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE);
            }

            if (!attribute.isNullable() && value == null) {
                throw new AttributeNonNullException(AttributeNonNullException.ATTRIBUTE_NULL_EXCEPTION, attribute.getName());
            }

            try {
                if (attribute.getType() == String.class && ((String) value).length() > attribute.getSize() && attribute.getSize() > -1) {
                    throw new AttributeSizeException(AttributeSizeException.ATTRIBUTE_SIZE_EXCEPTION, attribute.getName());
                }
            } catch (ClassCastException e) {
                throw new AttributeTypeMismatchException(AttributeTypeMismatchException.ATTRIBUTE_TYPE_MISMATCH, attribute.getType(), value.getClass(), attribute.getName());
            }


            if (descriptor.getIdentifier().getName().equalsIgnoreCase(fieldName)) {
                throw new AttributeUpdateException(AttributeUpdateException.ATTRIBUTE_UPDATE_IDENTIFIER, fieldName);
            }

            if (value != null) {
                if (value.getClass() != attribute.getType()) {
                    // Just a mutable immutable difference
                    if (value.getClass() == Integer.class && attribute.getType() == int.class
                            || value.getClass() == Long.class && attribute.getType() == long.class
                            || value.getClass() == Boolean.class && attribute.getType() == boolean.class
                            || value.getClass() == Double.class && attribute.getType() == double.class) {
                        continue;
                    } else if (value instanceof Integer && (attribute.getType() == Long.class || attribute.getType() == long.class)) {
                        instruction.setValue(Long.valueOf((Integer) value));
                        continue;
                    }
                    try {
                        instruction.setValue(attribute.getType().cast(value));
                    } catch (Exception e) {
                        throw new AttributeTypeMismatchException(AttributeTypeMismatchException.ATTRIBUTE_TYPE_MISMATCH, attribute.getType(), value.getClass(), attribute.getName());
                    }
                }

            }
        }

        return true;
    }
}
