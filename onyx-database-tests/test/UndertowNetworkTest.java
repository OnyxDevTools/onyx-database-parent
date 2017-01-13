import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
import org.junit.Ignore;
import org.junit.Test;
import org.xnio.Options;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.undertow.Handlers.websocket;

/**
 * Created by tosborn1 on 8/5/15.
 */
@Ignore
public class UndertowNetworkTest {
    @Test
    public void main() {

        ExecutorService threadPool = Executors.newFixedThreadPool(20);

        PathHandler pathHandler = Handlers.path().addPrefixPath("/test", websocket((exchange, channel) -> {
            ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
            client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setMaxPoolSize(16));
            client.getProperties().put(ClientProperties.SHARED_CONTAINER, true);
            Session session = null;
            try {
                session = (client.asyncConnectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig config) {

                    }
                }, new URI("ws://127.0.0.1:8101/" + "test")).get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Session mySession = session;

            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                protected void onFullTextMessage(final WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                    Runnable runnable = () -> WebSockets.sendText(message.getData(), channel, null);
//                    mySession.getAsyncRemote().sendText(message.getData());
                    threadPool.submit(runnable);
                }
            });
            channel.resumeReceives();
        }));


        Undertow undertow = Undertow.builder()
                .addHttpListener(8100, "0.0.0.0")
                .setServerOption(Options.ALLOW_BLOCKING, false)
                .setHandler(pathHandler).build();
        undertow.start();

        while (true) {
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
            }
        }
    }
}


