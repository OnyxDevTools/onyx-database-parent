package pojo;

import java.io.Serializable;
import java.util.List;

/**
 * Created by timothy.osborn on 4/14/15.
 */
public class ListObject implements Serializable
{
    public List objectArray;
    public List<Long> longArray;
    public List<Simple> simpleArray;

    public ListObject()
    {

    }
}
