package entities.index;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class MutableSequenceIdentifierEntityIndex extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public Long identifier;

    @Attribute
    public int correlation;

    @Attribute
    @Index
    public Long indexValue;

}
