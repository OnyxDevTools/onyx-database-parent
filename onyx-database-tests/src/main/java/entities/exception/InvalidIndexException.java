package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Index;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class InvalidIndexException extends ManagedEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String id = "asdf";

    @Index
    public String index = "";
}
