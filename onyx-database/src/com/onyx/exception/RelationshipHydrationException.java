package com.onyx.exception;

/**
 * Created by timothy.osborn on 3/20/15.
 *
 */
public class RelationshipHydrationException extends EntityException
{
    private static final String FAILURE_TO_HYDRATE = "Failure to hydrate relationship: ";

    /**
     * Constructor with message
     *
     * @param relationship Relationship attribute name
     * @param className Parent name
     * @param id Entity identifier
     */
    public RelationshipHydrationException(String relationship, String className, Object id)
    {
        super(FAILURE_TO_HYDRATE + relationship + " for class " + className + " with id " + String.valueOf(id));
    }

    @SuppressWarnings("unused")
    public RelationshipHydrationException()
    {
        super();
    }
}
