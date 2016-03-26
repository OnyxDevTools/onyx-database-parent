package memory;

import com.onyx.map.serializer.SocketBuffer;
import com.onyx.request.pojo.RequestEndpoint;
import com.onyx.request.pojo.RequestToken;
import com.onyx.request.pojo.RequestTokenType;
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
        entity.stringValue = "STring value";
        entity.dateValue = new Date(1483736263743l);
        entity.doublePrimitive = 342.23;
        entity.doubleValue = 232.2;
        entity.booleanPrimitive = true;
        entity.booleanValue = false;

        RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.DELETE, entity);

        final ByteBuffer buf =  SocketBuffer.serialize(token);
        RequestToken token2 = (RequestToken)SocketBuffer.deserialize(buf);

        Assert.assertTrue(token2.getMessageId() == token.getMessageId());



    }

    @Test
    public void testPerformance() throws IOException
    {


        long time = System.currentTimeMillis();

        List<AllAttributeEntity> entities = null;

        for(int i = 0; i < 20; i++)
        {

            entities = new ArrayList<>();
            for(int k = 0; k < 5000; k++)
            {

                final AllAttributeEntity entity = new AllAttributeEntity();
                entity.id = new BigInteger(130, random).toString(32);
                entity.longValue = 4l;
                entity.longPrimitive = 3l;
                entity.stringValue = "STring value";
                entity.dateValue = new Date(1483736263743l);
                entity.doublePrimitive = 342.23;
                entity.doubleValue = 232.2;
                entity.booleanPrimitive = true;
                entity.booleanValue = false;

                entities.add(entity);
            }

            RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.DELETE, entities);

            SocketBuffer.serialize(token);

        }

        System.out.println("Done Serializing in " + (System.currentTimeMillis() - time));
    }


}
