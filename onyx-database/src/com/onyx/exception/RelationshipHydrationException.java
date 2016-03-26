package com.onyx.exception;

/**
 * Created by timothy.osborn on 3/20/15.
 */
public class RelationshipHydrationException extends EntityException
{
    public static final String FAILURE_TO_HYDRATE = "Failure to hydrate relationship: ";

    /**
     * Constructor with message
     *
     * @param message
     */
    public RelationshipHydrationException(String message, String relationship, String className, Object id)
    {
        super(message + relationship + " for class " + className + " with id " + String.valueOf(id));
    }

    /**
     * Constructor with message
     *
     * @param relationship
     * @param className
     * @param id
     */
    public RelationshipHydrationException(String relationship, String className, Object id)
    {
        super(FAILURE_TO_HYDRATE + relationship + " for class " + className + " with id " + String.valueOf(id));
    }

    public RelationshipHydrationException()
    {
        super();
    }
}
