package com.onyx.persistence.annotations;

/**
 * This enum is used to indicate what primary key generator to use when declared within the @Identifier annotation
 *
 * NONE - No auto generated primary key
 *
 * SEQUENCE - Sequential primary key generated key
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * <pre>
 *     <code>
 *          {@literal @}Identifier(generator = IdentifierGenerator.SEQUENCE)
 *          {@literal @}Attribute(nullable = false, size = 200)
 *           public long personID;
 *     </code>
 * </pre>
 *
 *
 * @see com.onyx.persistence.annotations.Identifier
 */
public enum IdentifierGenerator
{
    SEQUENCE,
    NONE;

    /**
     * Constructor
     */
    IdentifierGenerator()
    {

    }
}
