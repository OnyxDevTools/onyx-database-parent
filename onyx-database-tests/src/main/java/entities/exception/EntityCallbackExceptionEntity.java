package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.PrePersist;


/**
 * Created by timothy.osborn on 2/10/15.
 */
@Entity
public class EntityCallbackExceptionEntity extends ManagedEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public long id;

    @PrePersist
    protected void onSomething() throws Exception
    {
        throw new Exception("HIYA");
    }


}
