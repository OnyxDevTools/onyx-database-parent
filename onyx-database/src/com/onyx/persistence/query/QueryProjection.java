package com.onyx.persistence.query;

import java.io.Serializable;

/**
 * Query projections.  This is a placeholder that has not been implemented yet
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * @deprecated
 */
public interface QueryProjection<T> extends Serializable {

    /**
     * @return the value of the mapped field
     */
    T getValue();

    /**
     * @return field name that the value will be mapped to in the result set
     */
    String getFieldName();


}
