package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
public class InheritedAttributeEntity extends AbstractInheritedAttributes implements IManagedEntity
{
    @Identifier
    @Attribute
    public String id;

}
