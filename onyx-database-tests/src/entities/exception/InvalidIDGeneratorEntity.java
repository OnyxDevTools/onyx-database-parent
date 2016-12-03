package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class InvalidIDGeneratorEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public String id;
}
