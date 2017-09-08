package pojo;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by timothy.osborn on 4/14/15.
 */
public class ComplexObject implements Serializable
{
    public ComplexObject()
    {

    }
    public ComplexObject mine;
    public ComplexObjectChild child;

    public Date dateValue = null;

}
