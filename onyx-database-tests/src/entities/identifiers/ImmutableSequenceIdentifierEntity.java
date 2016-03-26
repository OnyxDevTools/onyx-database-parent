package entities.identifiers;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class ImmutableSequenceIdentifierEntity extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public long identifier;

    @Attribute
    public int correlation;

}
