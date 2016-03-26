package pojo;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by timothy.osborn on 4/14/15.
 */
public class TransientValue implements Serializable
{
    public TransientValue()
    {

    }

    public int intValue;

    public transient long longValue;

    public Date zdateValue;
}
