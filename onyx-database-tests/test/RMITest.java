import com.onyx.client.auth.AuthenticationManager;
import com.onyx.client.rmi.OnyxRMIClient;
import com.onyx.server.rmi.OnyxRMIServer;
import org.junit.Test;
import rmi.IMessage;
import rmi.MessageImplementation;
import rmi.RMIClient;
import rmi.RMIMessageServer;

import java.rmi.RemoteException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RMITest {

    @Test
    public void testOnyxRMIPerformance() throws Exception {
        OnyxRMIServer rmiServer = new OnyxRMIServer();
        rmiServer.setPort(8080);
        rmiServer.register("A", new MessageImplementation(), IMessage.class);
        rmiServer.register("AUTH", (AuthenticationManager) (username, password) -> {
        }, AuthenticationManager.class);
        rmiServer.start();

        OnyxRMIClient rmiClient = new OnyxRMIClient();
        rmiClient.setAuthenticationManager((AuthenticationManager) rmiClient.getRemoteObject("AUTH", AuthenticationManager.class));
        rmiClient.connect("localhost", 8080);
        IMessage messanger = (IMessage) rmiClient.getRemoteObject("A", IMessage.class);

        byte[] message = new byte[2048];
        ExecutorService threadPool = Executors.newFixedThreadPool(16);
        CountDownLatch countDownLatch = new CountDownLatch(1000000);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000000; i++) {
            threadPool.execute(() -> {
                try {
                    messanger.captureMessage(message);
                    countDownLatch.countDown();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
        }

        countDownLatch.await();
        long stopTime = System.currentTimeMillis();

        System.out.println((stopTime - startTime));

    }

    @Test
    public void testRMIPerformance() throws Exception {
        RMIMessageServer.main(null);

        IMessage messenger = RMIClient.getMessage();

        byte[] message = new byte[2048];
        ExecutorService threadPool = Executors.newFixedThreadPool(16);
        CountDownLatch countDownLatch = new CountDownLatch(1000000);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000000; i++) {
            threadPool.execute(() -> {
                try {
                    messenger.captureMessage(message);
                    countDownLatch.countDown();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
        }

        countDownLatch.await();
        long stopTime = System.currentTimeMillis();

        System.out.println((stopTime - startTime));
    }
}
