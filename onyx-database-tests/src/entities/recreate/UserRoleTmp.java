package entities.recreate;


import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 9/21/14.
 */
@com.onyx.persistence.annotations.Entity
public class UserRoleTmp extends AbstractEntity implements IManagedEntity
{
    public UserRoleTmp()
    {

    }

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected Long id;

    public UserRoleTmp(TypeTmp type){
        //setType(type);
    }

    protected TypeTmp type;

    //@Attribute
    //protected int typeOrdinal = -1;
/*
    public TypeTmp getType() {
        if(typeOrdinal > -1)
        {
            return TypeTmp.values()[typeOrdinal];
        }
        return type;
    }

    public void setType(TypeTmp type)
    {
        this.type = type;
        if(type != null) {
            this.typeOrdinal = this.type.ordinal();
        }
        else
        {
            this.typeOrdinal = -1;
        }
        this.type = type;
    }*/

}
