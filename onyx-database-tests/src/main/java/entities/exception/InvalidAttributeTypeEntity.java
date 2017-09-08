package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class InvalidAttributeTypeEntity extends ManagedEntity implements IManagedEntity {

    @Attribute
    @Identifier
    public String id;

    @Attribute
    public float hiya;

    @Attribute
    public Object iduno;
}
