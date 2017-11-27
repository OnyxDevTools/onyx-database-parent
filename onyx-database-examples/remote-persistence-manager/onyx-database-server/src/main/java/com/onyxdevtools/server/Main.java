package com.onyxdevtools.server;

import com.onyx.application.impl.DatabaseServer;

import java.io.File;

@SuppressWarnings("WeakerAccess")
public class Main
{

    /**
     * Run Database Server
     *
     * ex:  executable /Database/Location/On/Disk 8080 admin admin
     *
     * @param args Command line arguments
     * @throws Exception General exception
     */
    public static void main(String[] args) throws Exception
    {
        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"remote-db.oxd";

        DatabaseServer server1 = new DatabaseServer(pathToOnyxDB);
        server1.setPort(8081);
        server1.setCredentials("onyx-remote", "SavingDataIsFun!");

        server1.start();
        System.out.println("Server Started");
        server1.join(); //joins the database thread with the application thread
        
    }

}
