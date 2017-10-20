package embedded;

import com.onyx.buffer.BufferPool;
import com.onyx.buffer.BufferStream;
import com.onyx.client.base.RequestToken;
import com.onyx.exception.OnyxException;
import entities.AllAttributeEntity;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import remote.base.RemoteBaseTest;

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 12/13/14.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SerializationTest extends RemoteBaseTest {


    @Test
    @Ignore
    public void testBasic() throws OnyxException
    {

        final AllAttributeEntity entity = new AllAttributeEntity();
        entity.id = new BigInteger(130, random).toString(32);
        entity.longValue = 4l;
        entity.longPrimitive = 3l;
        entity.stringValue = "STring key";
        entity.dateValue = new Date(1483736263743l);
        entity.doublePrimitive = 342.23;
        entity.doubleValue = 232.2;
        entity.booleanPrimitive = true;
        entity.booleanValue = false;

        RequestToken token = new RequestToken(Short.MIN_VALUE, entity);


        final ByteBuffer buf =  BufferStream.toBuffer(token);
        RequestToken token2 = (RequestToken) BufferStream.fromBuffer(buf);
        BufferPool.INSTANCE.recycle(buf);

        Assert.assertTrue(token2.getToken() == token.getToken());



    }

    @Test
    @Ignore
    public void testList() throws Exception
    {
        ArrayList arrayList = new ArrayList();
        arrayList.add(new AllAttributeEntity());

        ByteBuffer buffer = BufferStream.toBuffer(arrayList);
        buffer.rewind();

        ArrayList other = (ArrayList) BufferStream.fromBuffer(buffer);
        BufferPool.INSTANCE.recycle(buffer);

        assert other.size() == 1;
        assert other.get(0) instanceof AllAttributeEntity;
    }

    @Test
    @Ignore
    public void testPerformance() throws OnyxException
    {


        long time = System.currentTimeMillis();

        List<AllAttributeEntity> entities = null;

        for(int i = 0; i < 40; i++)
        {

            entities = new ArrayList<>();
            for(int k = 0; k < 5000; k++)
            {

                final AllAttributeEntity entity = new AllAttributeEntity();
                entity.id = new BigInteger(130, random).toString(32);
                entity.longValue = 4l;
                entity.longPrimitive = 3l;
                entity.stringValue = "STring key";
                entity.dateValue = new Date(1483736263743l);
                entity.doublePrimitive = 342.23;
                entity.doubleValue = 232.2;
                entity.booleanPrimitive = true;
                entity.booleanValue = false;

                entities.add(entity);
            }

            RequestToken token = new RequestToken(Short.MAX_VALUE, (Serializable)entities);

            ByteBuffer buffer = BufferStream.toBuffer(token);
            buffer.rewind();

            BufferStream.fromBuffer(buffer);
            BufferPool.INSTANCE.recycle(buffer);

        }

        System.out.println("Done Serializing in " + (System.currentTimeMillis() - time));
    }


}
