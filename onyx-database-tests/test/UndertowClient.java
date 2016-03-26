
import io.undertow.Handlers;
import io.undertow.Undertow;

import io.undertow.server.handlers.PathHandler;

import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;

import com.onyx.request.pojo.FilePathBody;
import com.onyx.request.pojo.RequestEndpoint;
import com.onyx.request.pojo.RequestToken;
import com.onyx.request.pojo.RequestTokenType;

import java.io.IOException;

import java.net.URI;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpoint;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;

import org.junit.Test;

import org.xnio.Options;

import static io.undertow.Handlers.websocket;


/**
 Created by tosborn1 on 8/5/15.
 */
public class UndertowClient
{
    CountDownLatch latch = new CountDownLatch(100001);

    public UndertowClient()
    {
    }

    @Test public void runBenchmark()
    {
        final PathHandler pathHandler = Handlers.path().addPrefixPath("/test", websocket((exchange, channel) ->
                {
                    channel.getReceiveSetter().set(new AbstractReceiveListener()
                        {
                            protected void onFullTextMessage(final WebSocketChannel channel, final BufferedTextMessage message)
                                throws IOException
                            {
                                latch.countDown();
                            }
                        });
                    channel.resumeReceives();
                }));

        final Undertow undertow = Undertow.builder().addHttpListener(8101, "0.0.0.0").setServerOption(Options.ALLOW_BLOCKING, false)
            .setHandler(pathHandler).build();
        undertow.start();

        final ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
        client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setMaxPoolSize(16));
        client.getProperties().put(ClientProperties.SHARED_CONTAINER, true);

        final ExecutorService service = Executors.newFixedThreadPool(20);

        try
        {
            final MyEndpoint endpoint = new MyEndpoint();

            final Session session = (client.asyncConnectToServer(endpoint, new URI("ws://52.27.241.209:8100/" + "test")).get(30,
                        TimeUnit.SECONDS));

            session.getAsyncRemote().sendText(
                "HelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHelloHello");
            Thread.sleep(1000);

            final long time = System.currentTimeMillis();

            for (int i = 0; i < 100000; i++)
            {
                final Runnable runnable = () ->
                {

                    try
                    {
                        final RequestToken token = new RequestToken(RequestEndpoint.FILE, RequestTokenType.FILE_SET_INVALID_CHUNKS,
                                new FilePathBody("", "", true));

                        // session.getAsyncRemote().sendBinary(RequestToken.getPacket(token));
                        session.getAsyncRemote().sendText("Hello");
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                };
                service.submit(runnable);
            }

            long after = System.currentTimeMillis();
            System.out.println(after - time);

            latch.await(99999999, TimeUnit.SECONDS);

            after = System.currentTimeMillis();
            System.out.println(after - time);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @ClientEndpoint public class MyEndpoint extends Endpoint
    {
        @Override public void onOpen(final Session session, final EndpointConfig endpointConfig)
        {
            // session.addMessageHandler(ByteBuffer.class, text -> {
            // latch.countDown();
            // });
            session.addMessageHandler(String.class, text -> { latch.countDown(); });
        }
    }
}
