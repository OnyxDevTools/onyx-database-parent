package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

/**
 * Created by timothy.osborn on 1/23/15.
 */
@Entity
public class ValidationEntity extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public Long id;

    @Attribute(nullable = false)
    public String requiredString;

    @Attribute(size = 10)
    public String maxSizeString;

}
