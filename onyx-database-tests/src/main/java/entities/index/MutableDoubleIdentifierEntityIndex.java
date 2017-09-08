package entities.index;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Index;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class MutableDoubleIdentifierEntityIndex extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public int identifier;

    @Attribute
    @Index
    public Double indexValue;

}
