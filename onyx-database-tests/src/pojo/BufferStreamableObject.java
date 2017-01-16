package pojo;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;

/**
 * Created by tosborn1 on 7/31/16.
 */
public class BufferStreamableObject implements BufferStreamable
{
    public String myString = null;
    public int myInt;

    public Simple simple = null;

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        myString = buffer.getString();
        myInt = buffer.getInt();
        simple = (Simple)buffer.getObject();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putString(myString);
        buffer.putInt(myInt);
        buffer.putObject(simple);
    }
}
