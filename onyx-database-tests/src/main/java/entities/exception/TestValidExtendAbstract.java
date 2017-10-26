package entities.exception;

import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.values.IdentifierGenerator;

/**
 * Created by Tim Osborn on 8/25/16.
 */
@Entity
public class TestValidExtendAbstract extends ValidAbstract {

    public TestValidExtendAbstract()
    {

    }
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected int myID;

    @Attribute
    protected int myAttribute;


}
