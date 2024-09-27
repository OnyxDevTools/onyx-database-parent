package com.onyx.persistence.annotations.values

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
 * @Identifier(generator = IdentifierGenerator.SEQUENCE)
 * @Attribute(nullable = false, size = 200)
 * public long personID;
 *
 * @see com.onyx.persistence.annotations.Identifier
 */
enum class IdentifierGenerator {
    SEQUENCE,UUID,NONE
}
