package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class InvalidAttributeTypeEntity implements IManagedEntity {

    @Attribute
    @Identifier
    public String id;

    @Attribute
    public float hiya;
}
