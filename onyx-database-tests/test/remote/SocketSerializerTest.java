package remote;

import category.RemoteServerTests;
import com.onyx.map.serializer.SocketBuffer;
import gnu.trove.THashMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import pojo.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by timothy.osborn on 4/14/15.
 */
@Category({ RemoteServerTests.class })
public class SocketSerializerTest
{
    @Test
    public void testSimple() throws IOException
    {
        Simple instance = new Simple();
        instance.hiya = 4;

        final ByteBuffer buffer = SocketBuffer.serialize(instance);
        buffer.rewind();

        Simple instance2 = (Simple)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance.hiya == instance2.hiya);
    }

    @Test
    public void testAllValues() throws IOException
    {
        AllTypes instance = new AllTypes();
        instance.intValue = 654;
        instance.intValueM = 246;
        instance.longValue = 998l;
        instance.longValueM = 999l ;
        instance.booleanValue = false;
        instance.booleanValueM = true;
        instance.shortValue = 44;
        instance.shortValueM = 45;
        instance.doubleValue = 12.123d;
        instance.doubleValueM = 23.124d;
        instance.floatValue = 11.2f;
        instance.floatValueM = 23.2f;
        instance.byteValue = ((byte)2);
        instance.byteValueM = Byte.valueOf((byte)3);
        instance.dateValue = new Date(3988);
        instance.stringValue = "Test String";
        instance.nullValue = null;
        instance.charValue = 'A';

        final ByteBuffer buffer = SocketBuffer.serialize(instance);
        buffer.rewind();

        AllTypes instance2 = (AllTypes)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance.intValue == instance2.intValue);
        Assert.assertTrue(instance.intValueM.equals(instance2.intValueM));
        Assert.assertTrue(instance.longValue == instance2.longValue);
        Assert.assertTrue(instance.longValueM.equals(instance2.longValueM));
        Assert.assertTrue(instance.booleanValue == instance2.booleanValue);
        Assert.assertTrue(instance.booleanValueM.equals(instance2.booleanValueM));
        Assert.assertTrue(instance.shortValueM.equals(instance2.shortValueM));
        Assert.assertTrue(instance.doubleValue == instance2.doubleValue);
        Assert.assertTrue(instance.shortValue == instance2.shortValue);
        Assert.assertTrue(instance.doubleValueM.equals(instance2.doubleValueM));
        Assert.assertTrue(instance.floatValue == instance2.floatValue);
        Assert.assertTrue(instance.floatValueM.equals(instance2.floatValueM));
        Assert.assertTrue(instance.byteValue == instance2.byteValue);
        Assert.assertTrue(instance.byteValueM.equals(instance2.byteValueM));
        Assert.assertTrue(instance.dateValue.getTime() == instance2.dateValue.getTime());
        Assert.assertTrue(instance.stringValue.equals(instance2.stringValue));
        Assert.assertTrue(instance.nullValue == instance2.nullValue);
        Assert.assertTrue(instance.charValue == instance2.charValue);
    }

    @Test
    public void testEnum() throws IOException
    {
        EnumTypeObject instance = new EnumTypeObject();
        instance.intValue = 44;
        instance.simpleEnum = SimpleEnum.SECOND;
        instance.longValue = 234235245l;

        final ByteBuffer buffer = SocketBuffer.serialize(instance);
        buffer.rewind();

        EnumTypeObject instance2 = (EnumTypeObject)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance.intValue == instance2.intValue);
        Assert.assertTrue(instance.longValue == instance2.longValue);
        Assert.assertTrue(instance.simpleEnum == instance2.simpleEnum);
    }

    @Test
    public void testTransient() throws IOException
    {
        TransientValue instance = new TransientValue();
        instance.intValue = 44;
        instance.longValue = 234l;
        instance.zdateValue = new Date(23423);

        final ByteBuffer buffer = SocketBuffer.serialize(instance);
        buffer.rewind();

        TransientValue instance2 = (TransientValue)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance.intValue == instance2.intValue);
        Assert.assertTrue(instance.longValue != instance2.longValue);
        Assert.assertTrue(instance.zdateValue.getTime() == instance2.zdateValue.getTime());
    }

    
    @Test
    public void testArrayObject() throws IOException
    {
        ArrayObject instance = new ArrayObject();
        instance.longArray = new Long[3];
        instance.objectArray = new Object[4];
        instance.simpleArray = new Simple[2];

        instance.longArray[0] = 223l;
        instance.longArray[1] = 293l;
        instance.longArray[2] = 323l;

        AllTypes obj = new AllTypes();
        obj.intValue = 23;

        instance.objectArray[3] = obj;

        instance.simpleArray[1] = new Simple();
        instance.simpleArray[1].hiya = 99;


        final ByteBuffer buffer = SocketBuffer.serialize(instance);
        buffer.rewind();

        ArrayObject instance2 = (ArrayObject)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance2.longArray[0] == 223l);
        Assert.assertTrue(instance2.longArray[1] == 293l);
        Assert.assertTrue(instance2.longArray[2] == 323l);

        Assert.assertTrue(instance2.objectArray[0] == null);
        Assert.assertTrue(instance2.objectArray[1] == null);
        Assert.assertTrue(instance2.objectArray[3] instanceof AllTypes);
        Assert.assertTrue(((AllTypes)instance2.objectArray[3]).intValue == 23);

        Assert.assertTrue(instance2.simpleArray[0] == null);
        Assert.assertTrue(instance2.simpleArray[1] instanceof Simple);
        Assert.assertTrue(instance2.simpleArray[1].hiya == 99);

    }

    @Test
    public void testListObject() throws IOException
    {
        ListObject instance = new ListObject();
        instance.longArray = new ArrayList();
        instance.objectArray = new ArrayList();
        instance.simpleArray = new ArrayList();

        instance.longArray.add(223l);
        instance.longArray.add(293l);
        instance.longArray.add(323l);

        AllTypes obj = new AllTypes();
        obj.intValue = 23;

        instance.objectArray.add(obj);

        instance.simpleArray.add(new Simple());
        instance.simpleArray.get(0).hiya = 99;


        final ByteBuffer buffer = SocketBuffer.serialize(instance);
        buffer.rewind();

        ListObject instance2 = (ListObject)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance2.longArray.get(0) == 223l);
        Assert.assertTrue(instance2.longArray.get(1) == 293l);
        Assert.assertTrue(instance2.longArray.get(2) == 323l);

        Assert.assertTrue(instance2.objectArray.get(0) instanceof AllTypes);
        Assert.assertTrue(((AllTypes)instance2.objectArray.get(0)).intValue == 23);

        Assert.assertTrue(instance2.simpleArray.get(0) instanceof Simple);
        Assert.assertTrue(instance2.simpleArray.get(0).hiya == 99);

    }

    @Test
    public void testMapSerialization() throws IOException
    {
        MapObject instance = new MapObject();
        instance.simpleMap = new HashMap();

        instance.simpleMap.put("NEW", new Simple());
        instance.simpleMap.put("NEW2", new Simple());
        instance.simpleMap.put("NEW3", new Simple());
        instance.simpleMap.put("NEW4", null);

        instance.simpleMap.get("NEW").hiya = 2324;
        instance.simpleMap.get("NEW3").hiya = 2924;

        instance.objectMap = new THashMap();
        instance.objectMap.put(new Long(23), new Integer(22));
        instance.objectMap.put(new Integer(12), false);
        instance.objectMap.put(new Simple(), new AllTypes());

        final ByteBuffer buffer = SocketBuffer.serialize(instance);
        buffer.rewind();

        MapObject instance2 = (MapObject)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance2.simpleMap.size() == 4);
        Assert.assertTrue(instance2.simpleMap.get("NEW").hiya == 2324);
        Assert.assertTrue(instance2.simpleMap.get("NEW3").hiya == 2924);
        Assert.assertTrue(instance2.simpleMap.get("NEW2").hiya == 3);
        Assert.assertTrue(instance2.simpleMap.get("NEW4") == null);

        Assert.assertTrue(instance2.objectMap.size() == 3);
        Assert.assertTrue(instance2.objectMap.get(new Long(23)).equals(22));
        Assert.assertTrue(instance2.objectMap.get(new Simple()).equals(new AllTypes()));
        Assert.assertTrue((boolean)instance2.objectMap.get(new Integer(12)) == false);
    }

    @Test
    public void testComplexObject() throws IOException
    {
        ComplexObject object = new ComplexObject();
        object.child = new ComplexObjectChild();
        object.child.parent = object;
        object.mine = object;

        object.dateValue = new Date(23423);
        object.child.longValue = 33l;

        final ByteBuffer buffer = SocketBuffer.serialize(object);
        buffer.rewind();

        ComplexObject instance2 = (ComplexObject)SocketBuffer.deserialize(buffer);

        Assert.assertTrue(instance2.dateValue.getTime() == object.dateValue.getTime());
        Assert.assertTrue(instance2.child.longValue == object.child.longValue);
        Assert.assertTrue(instance2.child.parent == instance2);
        Assert.assertTrue(instance2.mine == instance2);
    }

}

