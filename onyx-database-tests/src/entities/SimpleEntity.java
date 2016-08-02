package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

/**
 * Created by cosborn on 12/26/2014.
 */
@Entity
public class SimpleEntity extends AbstractEntity implements IManagedEntity {

    @Identifier(generator=IdentifierGenerator.NONE)
    @Attribute(size=255)
    public String simpleId;

    @Attribute(size=255)
    public String name;

    public String getSimpleId() {
        return simpleId;
    }

    public void setSimpleId(String simpleId) {
        this.simpleId = simpleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
