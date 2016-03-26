package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Identifier;

/**
 * Created by timothy.osborn on 12/14/14.
 */
public class NoEntityAnnotationClass implements IManagedEntity
{
    @Identifier
    @Attribute
    public String id;
}
