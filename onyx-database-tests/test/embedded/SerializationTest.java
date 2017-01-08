package embedded;

import com.onyx.request.pojo.RequestEndpoint;
import com.onyx.request.pojo.RequestToken;
import com.onyx.request.pojo.RequestTokenType;
import com.onyx.buffer.BufferStream;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import remote.base.RemoteBaseTest;
import entities.AllAttributeEntity;

import java.io.IOException;
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
    public void testBasic() throws IOException
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

        RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.DELETE, entity);


        final ByteBuffer buf =  BufferStream.toBuffer(token);
        RequestToken token2 = (RequestToken) BufferStream.fromBuffer(buf);

        Assert.assertTrue(token2.getMessageId() == token.getMessageId());



    }

    @Test
    public void testList() throws Exception
    {
        ArrayList arrayList = new ArrayList();
        arrayList.add(new AllAttributeEntity());

        ByteBuffer buffer = BufferStream.toBuffer(arrayList);
        buffer.rewind();

        ArrayList other = (ArrayList) BufferStream.fromBuffer(buffer);

        assert other.size() == 1;
        assert other.get(0) instanceof AllAttributeEntity;
    }

    @Test
    public void testPerformance() throws IOException
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

            RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.DELETE, entities);

            ByteBuffer buffer = BufferStream.toBuffer(token);
            buffer.rewind();

            BufferStream.fromBuffer(buffer);

        }

        System.out.println("Done Serializing in " + (System.currentTimeMillis() - time));
    }


}
