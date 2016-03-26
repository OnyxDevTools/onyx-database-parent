package pojo;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by timothy.osborn on 4/14/15.
 */
public class MapObject implements Serializable
{
    public Map<String, Simple> simpleMap = null;
    public Map objectMap = null;
    public Map<AllTypes, Long> complexKeyMap = null;

    public MapObject()
    {

    }
}
