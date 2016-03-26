package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity(cachable = false, cacheSize = 0)
public class InheritedLongAttributeEntity extends AbstractInheritedAttributes implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public long id;

}
