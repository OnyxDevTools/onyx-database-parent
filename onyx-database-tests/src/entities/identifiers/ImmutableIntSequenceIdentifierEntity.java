package entities.identifiers;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class ImmutableIntSequenceIdentifierEntity extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public int identifier;

    @Attribute
    public int correlation;

}
