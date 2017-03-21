package rmi;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIClient {

    public static IMessage getMessage() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            //Lookup message object from server
            return (IMessage) registry.lookup("Message");

        } catch (NotBoundException | RemoteException nbe) {
            nbe.printStackTrace();
        }
        return null;
    }
}