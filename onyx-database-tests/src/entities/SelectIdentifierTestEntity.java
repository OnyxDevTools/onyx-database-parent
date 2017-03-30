package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Index;

/**
 * Created by tosborn1 on 3/22/17.
 */
@Entity
public class SelectIdentifierTestEntity extends ManagedEntity {

    public SelectIdentifierTestEntity()
    {

    }

    @Identifier
    @Attribute
    public long id;

    @Attribute
    @Index
    public int index;

    @Attribute
    public String attribute;
}
