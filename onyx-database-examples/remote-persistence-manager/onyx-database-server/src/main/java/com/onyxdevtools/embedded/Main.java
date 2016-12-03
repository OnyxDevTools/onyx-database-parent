package com.onyxdevtools.persist;

import com.onyx.application.DatabaseServer;
import java.io.File;


public class Main
{

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
        String pathToOnyxDB = System.getProperty("user.home") 
                            + File.separatorChar + ".onyxdb" 
                            + File.separatorChar + "sandbox" 
                            + File.separatorChar +"remote-db.oxd";
        
        server1.setCredentials("onyx-remote", "SavingDataIsFun!");

        server1.setDatabaseLocation(pathToOnyxDB);
        server1.start();
        System.out.println("Server Started");
        server1.join(); //joins the database thread with the application thread
        
    }

}
