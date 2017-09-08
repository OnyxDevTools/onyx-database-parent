package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class NoIdEntity extends ManagedEntity implements IManagedEntity
{

    @Attribute
    public long attr;
}
