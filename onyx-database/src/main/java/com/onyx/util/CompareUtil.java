package com.onyx.util;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InvalidDataTypeForOperator;
import com.onyx.helpers.RelationshipHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by timothy.osborn on 12/14/14.
 *
 * This is a utility class used to compare values against various operators
 */
public class CompareUtil
{

    /**
     * This method compares but suppresses the error if values do not match
     * @param object First object to compare
     * @param object2 second object to compare
     * @param throwError ignored, by virtue of this method being called we assume to suppress errors
     * @return Whether the values are equal
     */
    @SuppressWarnings("unused")
    public static boolean compare(Object object, Object object2, boolean throwError)
    {
        try {
            return compare(object, object2);
        } catch (InvalidDataTypeForOperator invalidDataTypeForOperator) {
            return false;
        }
    }

    /**
     * This method runs a compare but, swallows the errors.
     *
     * @param object First object
     * @param object2 Second object to compare
     * @return Whether they are equal or not
     */
    public static boolean forceCompare(Object object, Object object2)
    {
        try {
            return compare(object ,object2);
        } catch (InvalidDataTypeForOperator invalidDataTypeForOperator) {
            return false;
        }
    }

    @SuppressWarnings("unchecked WeakerAccess")
    public static Object castObject(Class clazz, Object object) {

        Method method = null;

        if(object == null)
        {
            if(clazz == int.class)
                return 0;
            else if(clazz == long.class)
                return 0L;
            else if(clazz == double.class)
                return 0.0d;
            else if(clazz == float.class)
                return 0.0f;
            else if(clazz == boolean.class)
                return false;
            else if(clazz == char.class)
                return (char)0;
            else if(clazz == byte.class)
                return (byte)0;
            else if(clazz == short.class)
                return (short)0;
        }

        Class objectClass = object.getClass();
        if(clazz == int.class && objectClass == Integer.class)
            return object;
        else if(clazz == long.class && objectClass == Long.class)
            return object;
        else if(clazz == double.class && objectClass == Double.class)
            return object;
        else if(clazz == float.class && objectClass == Float.class)
            return object;
        else if(clazz == boolean.class && objectClass == Boolean.class)
            return object;
        else if(clazz == char.class && objectClass == Character.class)
            return object;
        else if(clazz == byte.class && objectClass == Byte.class)
            return object;
        else if(clazz == short.class && objectClass == Short.class)
            return object;
        else if(clazz == int.class && objectClass == Long.class)
            return ((Long)object).intValue();
        else if(clazz == long.class && objectClass == Integer.class)
            return ((Integer)object).longValue();

        try {
            if (clazz == Integer.class ||  clazz == int.class)
                return Integer.valueOf(""+object);
            else if (clazz == Long.class ||  clazz == long.class)
                return Long.valueOf(""+object);
            else if (clazz == Short.class ||  clazz == short.class)
                return Short.valueOf(""+object);
            else if (clazz == Byte.class ||  clazz == byte.class)
                return Byte.valueOf(""+object);
            else if (clazz == Boolean.class || clazz == boolean.class)
                return Boolean.valueOf(""+object);
            else if (clazz == Float.class  || clazz == int.class)
                return Float.valueOf(""+object);
            else if (clazz == Double.class || clazz == double.class)
                return Double.valueOf(""+object);
            else if (clazz == Character.class || clazz == char.class) {
                String stringVal = ""+object;
                if(stringVal.length() > 0)
                    return stringVal.charAt(0);
                return (char)0;
            }
            else if (clazz == String.class)
                method = objectClass.getMethod("toString");

            if (method == null)
                return object;

            return method.invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
        {
            if (clazz == String.class)
                return "" + object;

            return object;
        }
    }

    /**
     * Compare 2 objects, they can be any data type supported by the database
     *
     * @param object First object to compare
     * @param object2 second object to compare
     * @return Whether the values are equal
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public static boolean compare(Object object, Object object2) throws InvalidDataTypeForOperator
    {

        if(object == object2)
            return true;

        // If the objects do not match, cast it to the correct object
        if(object2 != null
                && object != null
                && !object2.getClass().isAssignableFrom(object.getClass()))
            object2 = castObject(object.getClass(), object2);

        // This was added because string.equals is much more efficient than using comparable.
        // Comparable iterated through the entire character array whereas .equals does not.
        if(object instanceof String &&
                object2 instanceof String)
            return object.equals(object2);

        else if(object instanceof Comparable
                && object2 instanceof Comparable)
            return ((Comparable) object).compareTo(object2) == 0;

        // Null checkers
        else if(object instanceof String && object == QueryCriteria.NULL_STRING_VALUE)
            return object2 == null;
        else if(object instanceof Double && (Double)object == QueryCriteria.NULL_DOUBLE_VALUE)
            return object2 == null;
        else if(object instanceof Long && (Long)object == QueryCriteria.NULL_LONG_VALUE)
            return object2 == null;
        else if(object instanceof Integer && (Integer)object == QueryCriteria.NULL_INTEGER_VALUE)
            return object2 == null;
        else if(object instanceof Boolean && object == QueryCriteria.NULL_BOOLEAN_VALUE && (object2 instanceof Boolean || object2 == null))
            return object2 == null;
        else if(object instanceof Date && object == QueryCriteria.NULL_DATE_VALUE)
            return object2 == null;
        else if(object2 == null && object instanceof Date && ((Date)object).getTime() == QueryCriteria.NULL_DATE_VALUE.getTime())
            return true;
        else if(object == null && object2 != null)
            return false;
        else if(object != null && object2 == null)
            return false;
        else if(object != null && object2 != null)
            return object.equals(object2);

        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw new InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR);
    }

    /**
     * Compare without throwing exception
     *
     * @param object First object to compare
     * @param object2 Second object to compare
     * @param operator Operator to compare against
     *
     * @return If the critieria meet return true
     */
    public static boolean forceCompare(Object object, Object object2, QueryCriteriaOperator operator)
    {
        try {
            return compare(object, object2, operator);
        } catch (InvalidDataTypeForOperator invalidDataTypeForOperator) {
            return false;
        }
    }

    /**
     * Generic method use to compare values with a given operator
     *
     * @param object First object to compare
     * @param object2 second object to compare
     * @param operator Any QueryCriteriaOperator
     * @return whether the objects meet the criteria of the operator
     * @throws InvalidDataTypeForOperator If the values cannot be compared
     */
    @SuppressWarnings("unchecked")
    public static boolean compare(Object object, Object object2, QueryCriteriaOperator operator) throws InvalidDataTypeForOperator
    {

        // If the objects do not match, cast it to the correct object
        if(object2 != null
                && object != null
                && !object2.getClass().isAssignableFrom(object.getClass()))
            object2 = castObject(object.getClass(), object2);

        if(operator == QueryCriteriaOperator.NOT_NULL)
            return (object2 != null);
        else if (operator == QueryCriteriaOperator.IS_NULL)
            return (object2 == null);

        // Equal - this should take a generic object key
        else if(operator == QueryCriteriaOperator.EQUAL)
            return compare(object, object2);

        // Not equal - this should take a generic object key
        else if(operator == QueryCriteriaOperator.NOT_EQUAL)
            return !compare(object, object2);

        // In, the first parameter must be a list of items if using the list key
        else if(operator == QueryCriteriaOperator.IN && object instanceof List)
        {
            List values = (List)object;
            for(Object value : values)
            {
                if(compare(value, object2))
                {
                    return true;
                }
            }
            return false;
        }

        // Not in, the first parameter must be a list of items if using the list key
        else if(operator == QueryCriteriaOperator.NOT_IN && object instanceof List)
        {
            List values = (List)object;
            for(Object value : values)
            {
                if(compare(value, object2))
                {
                    return false;
                }
            }
            return true;
        }

        // Contains , only valid for strings
        else if ((operator == QueryCriteriaOperator.CONTAINS || operator == QueryCriteriaOperator.NOT_CONTAINS)
                && object instanceof String
                && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return (operator == QueryCriteriaOperator.CONTAINS);
            }
            else if(object2 == null)
            {
                return !(operator == QueryCriteriaOperator.CONTAINS);
            }
            boolean retVal = ((String) object2).contains((String) object);

            return (operator == QueryCriteriaOperator.CONTAINS) == retVal;
        }

        // Like, only valid for strings
        else if ((operator == QueryCriteriaOperator.LIKE || operator == QueryCriteriaOperator.NOT_LIKE)
                && object instanceof String && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return (operator == QueryCriteriaOperator.LIKE);
            }
            else if(object2 == null)
            {
                return !(operator == QueryCriteriaOperator.LIKE);
            }
            boolean retVal = ((String) object2).equalsIgnoreCase((String) object);
            return (operator == QueryCriteriaOperator.LIKE) == retVal;
        }

        // Starts with, only valid for strings
        else if ((operator == QueryCriteriaOperator.STARTS_WITH || operator == QueryCriteriaOperator.NOT_STARTS_WITH)
                && (object instanceof String || object == null)
                && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return (operator == QueryCriteriaOperator.STARTS_WITH);
            }
            else if(object2 == null)
            {
                return !(operator == QueryCriteriaOperator.STARTS_WITH);
            }
            else if(object == null)
            {
                return !(operator == QueryCriteriaOperator.STARTS_WITH);
            }
            boolean retVal = ((String) object2).startsWith((String) object);
            return (operator == QueryCriteriaOperator.STARTS_WITH) == retVal;
        }

        // Not in, the first parameter must be a list of items if using the list key
        else if (operator == QueryCriteriaOperator.STARTS_WITH || operator == QueryCriteriaOperator.NOT_STARTS_WITH)
        {
            List values = (List)object;
            boolean startsWith = false;

            for(Object value : values)
            {
                if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
                {
                    return (operator == QueryCriteriaOperator.STARTS_WITH);
                }
                else if(object2 == null)
                {
                    return !(operator == QueryCriteriaOperator.STARTS_WITH);
                }
                else if(object == null)
                {
                    return !(operator == QueryCriteriaOperator.STARTS_WITH);
                }

                if (((String) object2).startsWith((String) value))
                {
                    startsWith = true;
                }

            }
            return (operator == QueryCriteriaOperator.STARTS_WITH) == startsWith;
        }

        // Matches, Use a regex, only valid with strings
        else if ((operator == QueryCriteriaOperator.MATCHES || operator == QueryCriteriaOperator.NOT_MATCHES)
                && object instanceof String && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return (operator == QueryCriteriaOperator.MATCHES);
            }
            else if(object2 == null)
            {
                return !(operator == QueryCriteriaOperator.MATCHES);
            }
            return (operator == QueryCriteriaOperator.MATCHES) == ((String) object2).matches((String) object);
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.GREATER_THAN)
        {
            if(object == null && object2 == null)
            {
                return false;
            }

            if(object2 == null)
            {
                return false;
            }
            else if(object == null)
            {
                return true;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object2).compareTo(object) > 0;
            }

            return false;
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.LESS_THAN)
        {
            if(object == null && object2 == null)
            {
                return false;
            }

            if(object2 == null)
            {
                return true;
            }
            else if(object == null)
            {
                return false;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object).compareTo(object2) > 0;
            }

            return false;
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.LESS_THAN_EQUAL)
        {
            if(object == null && object2 == null)
            {
                return true;
            }

            if(object2 == null)
            {
                return false;
            }
            else if(object == null)
            {
                return true;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object).compareTo(object2) >= 0;
            }

            return false;
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.GREATER_THAN_EQUAL)
        {
            if(object == null && object2 == null)
            {
                return true;
            }

            if(object2 == null)
            {
                return false;
            }
            else if(object == null)
            {
                return true;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object2).compareTo(object) >= 0;
            }

            return false;
        }

        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw new InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR);
    }

    /**
     * Relationship meets critieria.  This method will hydrate a relationship for an entity and
     * check its critieria to ensure the critieria is met
     *
     * @param entity Original entity containing the relationship.  This entity may or may not have
     *               hydrated relationships.  For that reason we have to go back to the store to
     *               retrieve the relationship entitities.
     *
     * @param entityReference Used for quick reference so we do not have to retrieve the entitiies
     *                        reference before retrieving the relationship.
     *
     * @param criteria Critieria to check for to see if we meet the requirements
     *
     * @param context Schem context used to pull entity descriptors and record controllers and such
     *
     * @return Whether the relationship value has met all of the critieria
     *
     * @throws OnyxException Something bad happened.
     *
     * @since 1.3.0 - Used to remove the dependency on relationship scanners and to allow query caching
     *                to do a quick reference to see if newly saved entities meet the critieria
     */
    private static boolean relationshipMeetsCritieria(IManagedEntity entity, Object entityReference, QueryCriteria criteria, SchemaContext context) throws OnyxException
    {
        boolean meetsCritiera = false;
        final QueryCriteriaOperator operator = criteria.getOperator();

        // Grab the relationship from the store
        final List<IManagedEntity> relationshipEntities = RelationshipHelper.getRelationshipForValue(entity, entityReference, criteria.getAttribute(), context);

        // If there are relationship values, check to see if they meet critieria
        if(relationshipEntities.size() > 0)
        {
            String[] items = criteria.getAttribute().split("\\.");
            String attribute = items[items.length - 1];
            final OffsetField offsetField = ReflectionUtil.getOffsetField(relationshipEntities.get(0).getClass(), attribute);

            // All we need is a single match.  If there is a relationship that meets the criteria, move along
            for(IManagedEntity relationshipEntity : relationshipEntities)
            {
                meetsCritiera = CompareUtil.compare(criteria.getValue(), ReflectionUtil.getAny(relationshipEntity, offsetField), operator);
                if(meetsCritiera)
                    break;
            }
        }
        return meetsCritiera;
    }


    /**
     * Entity meets the query critieria.  This method is used to determine whether the entity meets all the
     * critieria of the query.  It was implemented so that we no longer have logic in the query controller
     * to sift through scans.  We can now only perform a full table scan once.
     *
     * @param allCritieria Contribed list of criteria to
     * @param rootCriteria Critieria to verify whether the entity meets
     * @param entity Entity to check for criteria
     * @param entityReference The entities reference
     * @param context Schema context used to pull entity descriptors, and such
     * @param descriptor Quick reference to the entities descriptor so we do not have to pull it from the schema context
     * @return Whether the entity meets all the critieria.
     * @throws OnyxException Cannot hydrate or pull an attribute from an entity
     *
     * @since 1.3.0 Simplified query criteria management
     */
    public static boolean meetsCriteria(Set<QueryCriteria> allCritieria, QueryCriteria rootCriteria, IManagedEntity entity, Object entityReference, SchemaContext context, EntityDescriptor descriptor) throws OnyxException {

        boolean subCreriaMet;

        // Iterate through
        for(QueryCriteria criteria1 : allCritieria)
        {
            if(criteria1.getAttribute().contains("."))
            {
                // Compare operator for relationship object
                subCreriaMet = relationshipMeetsCritieria(entity, entityReference, criteria1, context);
            }
            else
            {
                // Compare operator for attribute object
                if(criteria1.getAttributeDescriptor() == null)
                    criteria1.setAttributeDescriptor(descriptor.getAttributes().get(criteria1.getAttribute()));
                final OffsetField offsetField = criteria1.getAttributeDescriptor().getField();
                subCreriaMet = CompareUtil.compare(criteria1.getValue(), ReflectionUtil.getAny(entity, offsetField), criteria1.getOperator());
            }

            criteria1.meetsCritieria = subCreriaMet;
        }

        return calculateCritieriaMet(rootCriteria);
    }

    /**
     * Calculates the result of the parent critieria and correlates
     * its set of children criteria.  A pre-requisite to invoking this method
     * is that all of the criteria have the meet criteria field set and
     * it does NOT take into account the not modifier in the pre-requisite.
     *
     *
     * @param criteria Root critiera to check.  This maintains the order of operations
     * @return Whether all the crierita are met taking into account the order of operations
     *         and the not() modifier
     *
     * @since 1.3.0 Added to enhance insertion based criteria checking
     */
    private static boolean calculateCritieriaMet(QueryCriteria criteria)
    {
        boolean meetsCritieria = criteria.meetsCritieria;

        if(criteria.getSubCriteria().size() > 0) {
            for (QueryCriteria subCritieria : criteria.getSubCriteria()) {
                if (subCritieria.isOr()) {
                    meetsCritieria = (calculateCritieriaMet(subCritieria) || meetsCritieria);
                } else {
                    meetsCritieria = (calculateCritieriaMet(subCritieria) && meetsCritieria);
                }
            }
        }

        if(criteria.isNot())
            meetsCritieria = !meetsCritieria;
        return meetsCritieria;
    }
}
