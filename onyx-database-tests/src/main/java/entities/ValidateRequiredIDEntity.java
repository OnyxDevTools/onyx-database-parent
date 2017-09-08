package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

/**
 * Created by timothy.osborn on 2/10/15.
 */
@Entity
public class ValidateRequiredIDEntity extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String id;

    @Attribute(nullable = false)
    public String requiredString;

    @Attribute(size = 10)
    public String maxSizeString;

}
