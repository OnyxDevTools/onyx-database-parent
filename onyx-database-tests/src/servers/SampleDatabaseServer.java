package servers;

import com.onyx.application.WebDatabaseServer;
import entities.SimpleEntity;

/**
 * Created by timothy.osborn on 4/1/15.
 */
public class SampleDatabaseServer extends WebDatabaseServer
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
        SampleDatabaseServer server1 = new SampleDatabaseServer();
        server1.setPort(8080);
        server1.setWebServicePort(8082);
        server1.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
        server1.start();

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setName("Test Name");
        simpleEntity.setSimpleId("ASDF");

        server1.join();
        System.out.println("Started");
    }

}
