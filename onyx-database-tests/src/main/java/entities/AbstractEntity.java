package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;

import java.util.Date;

/**
 * Created by timothy.osborn on 10/20/14.
 */
public abstract class AbstractEntity extends ManagedEntity
{

    @Attribute
    public Date dateCreated;

    @Attribute
    public Date dateUpdated;

    public double doubleSample;
    public Double dblSmaple;

}
