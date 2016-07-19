package servers;

import com.onyx.application.DatabaseServer;

/**
 * Created by timothy.osborn on 4/1/15.
 */
public class SampleDatabaseServer extends DatabaseServer
{
    public SampleDatabaseServer()
    {

    }

    /**
     * Run Database Server
     *
     * ex:  executable /Database/Location/On/Disk 8080 admin admin
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception
    {
        DatabaseServer server1 = new DatabaseServer();
        server1.setPort(8080);
        server1.setEnableSocketSupport(true);
        server1.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
        server1.start();
        server1.join();
        System.out.println("Started");
    }

}
