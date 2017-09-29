package embedded;

import com.onyx.buffer.BufferStream;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.Contexts;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import entities.AllAttributeEntity;
import entities.SimpleEntity;
import entities.index.StringIdentifierEntityIndex;
import entities.relationship.OneToOneChild;
import entities.relationship.OneToOneParent;
import org.junit.Ignore;
import org.junit.Test;
import pojo.AllTypes;
import pojo.BufferStreamableObject;
import pojo.Simple;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Created by tosborn1 on 7/28/16.
 */
public class BufferStreamTest {

    @Test
    public void testSerializePrimitive() throws BufferingException {
        // int
        ByteBuffer buffer = serialize(1);
        int value = (int) deserialize(buffer);
        assert value == 1;
        BufferStream.recycle(buffer);

        // long
        buffer = serialize(2l);
        assert (long) deserialize(buffer) == 2l;
        BufferStream.recycle(buffer);

        // boolean
        buffer = serialize(true);
        assert (boolean) deserialize(buffer) == true;
        BufferStream.recycle(buffer);

        // short
        buffer = serialize((short) 34);
        assert (short) deserialize(buffer) == (short) 34;
        BufferStream.recycle(buffer);

        // byte
        buffer = serialize((byte) 3);
        assert (byte) deserialize(buffer) == (byte) 3;
        BufferStream.recycle(buffer);

        // float
        buffer = serialize(3.3f);
        assert (float) deserialize(buffer) == 3.3f;
        BufferStream.recycle(buffer);

        // double
        buffer = serialize(3.33d);
        assert (double) deserialize(buffer) == 3.33d;
        BufferStream.recycle(buffer);

        // char
        buffer = serialize('C');
        assert (char) deserialize(buffer) == 'C';
        BufferStream.recycle(buffer);

    }

    @Test
    public void testPrimitiveArrays() throws BufferingException {
        int[] ints = {1, 2, 3, 4};
        ByteBuffer buffer = serialize(ints);
        ints = (int[]) deserialize(buffer);
        assert ints[0] == 1 && ints[1] == 2 && ints[2] == 3 && ints[3] == 4;
        BufferStream.recycle(buffer);

        long[] longs = {1l, 2l, 3l, 4l};
        buffer = serialize(longs);
        longs = (long[]) deserialize(buffer);
        assert longs[0] == 1l && longs[1] == 2l && longs[2] == 3l && longs[3] == 4l;
        BufferStream.recycle(buffer);

        byte[] bytes = {1, 2, 3, 4};
        buffer = serialize(bytes);
        bytes = (byte[]) deserialize(buffer);
        assert bytes[0] == 1 && bytes[1] == 2 && bytes[2] == 3 && bytes[3] == 4;
        BufferStream.recycle(buffer);

        boolean[] booleans = {true, false, true, false};
        buffer = serialize(booleans);
        booleans = (boolean[]) deserialize(buffer);
        assert booleans[0] == true && booleans[1] == false && booleans[2] == true && booleans[3] == false;
        BufferStream.recycle(buffer);

        float[] floats = {1.1f, 2.2f, 3.3f, 4.4f};
        buffer = serialize(floats);
        floats = (float[]) deserialize(buffer);
        assert floats[0] == 1.1f && floats[1] == 2.2f && floats[2] == 3.3f && floats[3] == 4.4f;
        BufferStream.recycle(buffer);

        double[] doubles = {1.1, 2.2, 3.3, 4.4};
        buffer = serialize(doubles);
        doubles = (double[]) deserialize(buffer);
        assert doubles[0] == 1.1 && doubles[1] == 2.2 && doubles[2] == 3.3 && doubles[3] == 4.4;
        BufferStream.recycle(buffer);

        short[] shorts = {1, 2, 3, 4};
        buffer = serialize(shorts);
        shorts = (short[]) deserialize(buffer);
        assert shorts[0] == 1 && shorts[1] == 2 && shorts[2] == 3 && shorts[3] == 4;
        BufferStream.recycle(buffer);

        char[] chars = {'1', '2', '3', '4'};
        buffer = serialize(chars);
        chars = (char[]) deserialize(buffer);
        assert chars[0] == '1' && chars[1] == '2' && chars[2] == '3' && chars[3] == '4';
        BufferStream.recycle(buffer);
    }

    @Test
    public void testString() throws BufferingException {
        String value = "ASDF@#$@#$ASDFASDF";
        ByteBuffer buffer = serialize(value);
        String otherValue = (String) deserialize(buffer);
        assert value.equals(otherValue);
        BufferStream.recycle(buffer);
    }

    @Test
    public void testDate()  throws BufferingException{
        Date value = new Date(3737373);
        ByteBuffer buffer = serialize(value);
        Date otherValue = (Date) deserialize(buffer);
        assert value.equals(otherValue);
        BufferStream.recycle(buffer);
    }

    @Test
    @Ignore
    public void testNamedObject() throws BufferingException{

        AllAttributeEntity entity = new AllAttributeEntity();
        entity.booleanPrimitive = false;
        entity.booleanValue = true;
        entity.longValue = 4l;
        entity.longPrimitive = 3l;
        entity.intValue = 23;
        entity.intPrimitive = 22;
        entity.stringValue = "234234234";
        entity.dateValue = new Date(333333);
        entity.doublePrimitive = 23.33;
        entity.doubleValue = 22.2;

        ByteBuffer buffer = serialize(entity);
        AllAttributeEntity otherValue = (AllAttributeEntity) deserialize(buffer);
        BufferStream.recycle(buffer);

        assert otherValue.booleanPrimitive == false;
        assert otherValue.booleanValue == true;
        assert otherValue.longValue == 4l;
        assert otherValue.longPrimitive == 3l;
        assert otherValue.intValue == 23;
        assert otherValue.intPrimitive == 22;
        assert otherValue.stringValue.equals("234234234");
        assert otherValue.dateValue.equals(new Date(333333));
        assert otherValue.doublePrimitive == 23.33;
        assert otherValue.doubleValue == 22.2;
    }

    @Test
    public void testNonNamedObject() throws BufferingException {
        AllTypes entity = new AllTypes();
        entity.booleanValueM = false;
        entity.booleanValue = true;
        entity.longValue = 4l;
        entity.longValueM = 3l;
        entity.intValue = 23;
        entity.intValueM = 22;
        entity.stringValue = "234234234";
        entity.dateValue = new Date(333333);
        entity.shortValue = 26;
        entity.shortValueM = 95;
        entity.doubleValue = 32.32;
        entity.doubleValueM = 22.54;
        entity.floatValue = 32.321f;
        entity.floatValueM = 22.542f;
        entity.byteValue = (byte) 4;
        entity.byteValueM = (byte) 9;
        entity.nullValue = null;
        entity.charValue = 'K';
        entity.charValueM = 'U';


        ByteBuffer buffer = serialize(entity);
        AllTypes entity2 = (AllTypes) deserialize(buffer);
        BufferStream.recycle(buffer);

        assert entity2.booleanValueM == false;
        assert entity2.booleanValue == true;
        assert entity2.longValue == 4l;
        assert entity2.longValueM == 3l;
        assert entity2.intValue == 23;
        assert entity2.intValueM == 22;
        assert entity2.stringValue.equals("234234234");
        assert entity2.dateValue.equals(new Date(333333));
        assert entity2.shortValue == 26;
        assert entity2.shortValueM == 95;
        assert entity2.doubleValue == 32.32;
        assert entity2.doubleValueM == 22.54;
        assert entity2.floatValue == 32.321f;
        assert entity2.floatValueM == 22.542f;
        assert entity2.byteValue == (byte) 4;
        assert entity2.byteValueM == (byte) 9;
        assert entity2.nullValue == null;
        assert entity2.charValue == 'K';
        assert entity2.charValueM == 'U';
    }

    @Test
    public void testMapPrimitives() throws BufferingException {
        Map<Integer, Object> map = new HashMap();
        map.put(1, 3);
        map.put(4, 6l);
        map.put(6, 22.2);
        map.put(3, 23.3f);
        map.put(5, true);
        map.put(9, (short) 23);
        map.put(99, (byte) 32);
        map.put(87, 'C');
        map.put(100, new int[]{3, 4, 6, 3});

        ByteBuffer buffer = serialize(map);
        Map<Integer, Object> other = (Map) deserialize(buffer);
        BufferStream.recycle(buffer);

        assert (int) other.get(1) == 3;
        assert (long) other.get(4) == 6l;
        assert (double) other.get(6) == 22.2;
        assert (float) other.get(3) == 23.3f;
        assert (boolean) other.get(5) == true;
        assert (short) other.get(9) == (short) 23;
        assert (byte) other.get(99) == (byte) 32;
        assert (char) other.get(87) == 'C';
        assert ((int[]) other.get(100))[0] == 3;
        assert ((int[]) other.get(100))[1] == 4;
        assert ((int[]) other.get(100))[2] == 6;
        assert ((int[]) other.get(100))[3] == 3;

    }

    @Test
    public void testCollection() throws BufferingException {
        Collection myCollection = new ArrayList();
        for (int i = 0; i < 10000; i++) {
            AllTypes entity = new AllTypes();
            entity.booleanValueM = false;
            entity.booleanValue = true;
            entity.longValue = 4l;
            entity.longValueM = 3l;
            entity.intValue = i;
            entity.intValueM = 22;
            entity.stringValue = "234234234";
            entity.dateValue = new Date(333333);
            entity.shortValue = 26;
            entity.shortValueM = 95;
            entity.doubleValue = 32.32;
            entity.doubleValueM = 22.54;
            entity.floatValue = 32.321f;
            entity.floatValueM = 22.542f;
            entity.byteValue = (byte) 4;
            entity.byteValueM = (byte) 9;
            entity.nullValue = null;
            entity.charValue = 'K';
            entity.charValueM = 'U';
            myCollection.add(entity);
        }

        ByteBuffer buffer = serialize(myCollection);
        Collection collection2 = (Collection) deserialize(buffer);
        BufferStream.recycle(buffer);

        int i = 0;
        for (Object obj : collection2) {
            AllTypes entity = (AllTypes) obj;

            assert entity.booleanValueM == false;
            assert entity.booleanValue == true;
            assert entity.longValue == 4l;
            assert entity.longValueM == 3l;
            assert entity.intValue == i;
            assert entity.intValueM == 22;
            assert entity.stringValue.equals("234234234");
            assert entity.dateValue.equals(new Date(333333));
            assert entity.shortValue == 26;
            assert entity.shortValueM == 95;
            assert entity.doubleValue == 32.32;
            assert entity.doubleValueM == 22.54;
            assert entity.floatValue == 32.321f;
            assert entity.floatValueM == 22.542f;
            assert entity.byteValue == (byte) 4;
            assert entity.byteValueM == (byte) 9;
            assert entity.nullValue == null;
            assert entity.charValue == 'K';
            assert entity.charValueM == 'U';

            i++;

        }
    }

    @Test
    public void testMap() throws BufferingException{
        Collection myCollection = new ArrayList();
        for (int i = 0; i < 10000; i++) {
            AllTypes entity = new AllTypes();
            entity.booleanValueM = false;
            entity.booleanValue = true;
            entity.longValue = 4l;
            entity.longValueM = 3l;
            entity.intValue = i;
            entity.intValueM = 22;
            entity.stringValue = "234234234";
            entity.dateValue = new Date(333333);
            entity.shortValue = 26;
            entity.shortValueM = 95;
            entity.doubleValue = 32.32;
            entity.doubleValueM = 22.54;
            entity.floatValue = 32.321f;
            entity.floatValueM = 22.542f;
            entity.byteValue = (byte) 4;
            entity.byteValueM = (byte) 9;
            entity.nullValue = null;
            entity.charValue = 'K';
            entity.charValueM = 'U';
            myCollection.add(entity);
        }

        ByteBuffer buffer = serialize(myCollection);
        Collection collection2 = (Collection) deserialize(buffer);
        BufferStream.recycle(buffer);

        int i = 0;
        for (Object obj : collection2) {
            AllTypes entity = (AllTypes) obj;

            assert entity.booleanValueM == false;
            assert entity.booleanValue == true;
            assert entity.longValue == 4l;
            assert entity.longValueM == 3l;
            assert entity.intValue == i;
            assert entity.intValueM == 22;
            assert entity.stringValue.equals("234234234");
            assert entity.dateValue.equals(new Date(333333));
            assert entity.shortValue == 26;
            assert entity.shortValueM == 95;
            assert entity.doubleValue == 32.32;
            assert entity.doubleValueM == 22.54;
            assert entity.floatValue == 32.321f;
            assert entity.floatValueM == 22.542f;
            assert entity.byteValue == (byte) 4;
            assert entity.byteValueM == (byte) 9;
            assert entity.nullValue == null;
            assert entity.charValue == 'K';
            assert entity.charValueM == 'U';

            i++;

        }
    }

    @Test
    public void testRandomObjectArray() throws BufferingException {
        ArrayList objects = new ArrayList();
        objects.add(1);
        objects.add(1l);

        AllTypes entity = new AllTypes();
        entity.booleanValueM = false;
        entity.booleanValue = true;
        entity.longValue = 4l;
        entity.longValueM = 3l;
        entity.intValue = 55;
        entity.intValueM = 22;
        entity.stringValue = "234234234";
        entity.dateValue = new Date(333333);
        entity.shortValue = 26;
        entity.shortValueM = 95;
        entity.doubleValue = 32.32;
        entity.doubleValueM = 22.54;
        entity.floatValue = 32.321f;
        entity.floatValueM = 22.542f;
        entity.byteValue = (byte) 4;
        entity.byteValueM = (byte) 9;
        entity.nullValue = null;
        entity.charValue = 'K';
        entity.charValueM = 'U';

        objects.add(entity);
        objects.add(true);


        ByteBuffer buffer = serialize(objects);
        ArrayList collection2 = (ArrayList) deserialize(buffer);
        BufferStream.recycle(buffer);


        assert (int) collection2.get(0) == 1;
        assert (long) collection2.get(1) == 1l;
        assert (boolean) collection2.get(3) == true;

        entity = (AllTypes) collection2.get(2);

        assert entity.booleanValueM == false;
        assert entity.booleanValue == true;
        assert entity.longValue == 4l;
        assert entity.longValueM == 3l;
        assert entity.intValue == 55;
        assert entity.intValueM == 22;
        assert entity.stringValue.equals("234234234");
        assert entity.dateValue.equals(new Date(333333));
        assert entity.shortValue == 26;
        assert entity.shortValueM == 95;
        assert entity.doubleValue == 32.32;
        assert entity.doubleValueM == 22.54;
        assert entity.floatValue == 32.321f;
        assert entity.floatValueM == 22.542f;
        assert entity.byteValue == (byte) 4;
        assert entity.byteValueM == (byte) 9;
        assert entity.nullValue == null;
        assert entity.charValue == 'K';
        assert entity.charValueM == 'U';
    }

    @Test
    @Ignore
    public void testRecursiveEntry() throws BufferingException
    {
        OneToOneParent parent = new OneToOneParent();
        parent.child = new OneToOneChild();
        parent.child.parent = parent;
        parent.correlation = 322;
        parent.identifier = "THIS IS AN ID";
        parent.child.correlation = 99;
        parent.child.identifier = "CHILD ID";

        ByteBuffer buffer = serialize(parent);
        OneToOneParent parent1 = (OneToOneParent)deserialize(buffer);
        BufferStream.recycle(buffer);

        assert parent1.child.correlation == 99;
        assert parent1.child.parent == parent1;
        assert parent1.child.identifier.equals("CHILD ID");
        assert parent1.correlation == 322;
        assert parent1.identifier.equals("THIS IS AN ID");

    }

    @Test
    @Ignore
    public void testNamedObjectArray() throws BufferingException {
        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.simpleId = "HIYA";
        simpleEntity.name = "NAME";
        Object[] objects = new Object[5];
        objects[0] = 1l;
        objects[1] = 22.32f;
        objects[2] = "This is a string";
        objects[3] = simpleEntity;
        objects[4] = 3;

        ByteBuffer buffer = serialize(objects);
        Object[] objects1 = (Object[]) deserialize(buffer);
        BufferStream.recycle(buffer);

        assert objects1.length == 5;
        assert (long) objects1[0] == 1l;
        assert (float) objects1[1] == 22.32f;
        assert objects1[2].equals("This is a string");
        assert ((SimpleEntity) objects1[3]).simpleId.equals("HIYA");
        assert ((SimpleEntity) objects1[3]).name.equals("NAME");
        assert (int) objects[4] == 3;

        long time = System.currentTimeMillis();

        for (int i = 0; i < 100000; i++) {
            buffer = serialize(objects);
            deserialize(buffer);
            BufferStream.recycle(buffer);
        }

        long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time));
    }

    @Test
    public void testObjectWithEnum() throws BufferingException
    {
        Query query = new Query(SimpleEntity.class, new QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, 33l));

        ByteBuffer buffer = serialize(query);
        Query query1 = (Query)deserialize(buffer);
        BufferStream.recycle(buffer);

        assert query.getEntityType() == query1.getEntityType();
        assert query.getCriteria().getType() == query1.getCriteria().getType();
        assert query.getCriteria().getAttribute().equals(query1.getCriteria().getAttribute());
        assert query.getCriteria().getLongValue().equals(query1.getCriteria().getLongValue());
    }

    @Test
    public void testBufferable() throws BufferingException
    {
        BufferStreamableObject bufferableObject = new BufferStreamableObject();
        bufferableObject.setMyInt(33);
        bufferableObject.setMyString("This");
        bufferableObject.setSimple(new Simple());
        bufferableObject.getSimple().hiya = 2;

        ByteBuffer buffer = serialize(bufferableObject);
        BufferStreamableObject bufferableObject1 = (BufferStreamableObject) deserialize(buffer);
        BufferStream.recycle(buffer);

        assert bufferableObject1 != null;
        assert bufferableObject1.getMyInt() == 33;
        assert bufferableObject1.getMyString().equals("This");
        assert bufferableObject1.getSimple().hiya == 2;

    }

    @Test
    public void testNestedCollection() throws BufferingException
    {
        Query findQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"));
        ByteBuffer buffer = serialize(findQuery);
        Query query = (Query) deserialize(buffer);
        BufferStream.recycle(buffer);

        assert findQuery.getEntityType() == query.getEntityType();
        assert findQuery.getCriteria().getIntegerValue().equals(query.getCriteria().getIntegerValue());
        assert findQuery.getCriteria().getType().equals(query.getCriteria().getType());
        assert findQuery.getCriteria().getSubCriteria().get(0).getAttribute().equals(query.getCriteria().getSubCriteria().get(0).getAttribute());
        assert findQuery.getCriteria().getSubCriteria().get(0).getStringValue().equals(query.getCriteria().getSubCriteria().get(0).getStringValue());

        assert findQuery.getCriteria().getOperator().equals(query.getCriteria().getOperator());
        assert findQuery.getMaxResults() == query.getMaxResults();
    }

    public static ByteBuffer serialize(Object object) throws BufferingException
    {
        return BufferStream.toBuffer(object);
    }

    public static Object deserialize(ByteBuffer buffer) throws BufferingException
    {
        return BufferStream.fromBuffer(buffer);
    }

}
