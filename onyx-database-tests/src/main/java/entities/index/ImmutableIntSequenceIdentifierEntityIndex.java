package entities.index;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class ImmutableIntSequenceIdentifierEntityIndex extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public int identifier;

    @Attribute
    public int correlation;

    @Attribute
    @Index
    public int indexValue;

}
