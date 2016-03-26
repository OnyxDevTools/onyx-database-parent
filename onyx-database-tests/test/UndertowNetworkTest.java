import com.onyx.request.pojo.RequestToken;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
import org.junit.Test;
import org.xnio.Options;

import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.*;

import static io.undertow.Handlers.websocket;

/**
 * Created by tosborn1 on 8/5/15.
 */
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


