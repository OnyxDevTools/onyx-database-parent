package com.onyx.helpers;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IdentifierDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.*;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.AttributeUpdate;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 1/20/15.
 * <p>
 * Validate an entity
 */
@Deprecated
public class ValidationHelper {

    @Deprecated
    public static boolean isDefaultQuery(EntityDescriptor descriptor, Query query) {
        return query.getCriteria() == null || query.getCriteria().getSubCriteria().size() <= 0 && (query.getCriteria().getOperator() == QueryCriteriaOperator.NOT_NULL && query.getCriteria().getAttribute().equals(descriptor.getIdentifier().getName()));
    }
}
