package pojo;

import java.io.Serializable;

public class Simple implements Serializable
{
    public Simple()
    {

    }
    public int hiya = 3;

    public int hashCode()
    {
        return hiya;
    }

    public boolean equals(Object val)
    {
        if(val instanceof Simple)
        {
            return ((Simple) val).hiya == hiya;
        }

        return false;
    }
}
