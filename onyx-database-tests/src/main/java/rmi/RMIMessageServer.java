package rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIMessageServer {
    public static void main(String[] args) {
        try {
            //Declare message object
            MessageImplementation msgObject =
                    new MessageImplementation();

            //Create and get reference from registry
            Registry registry = LocateRegistry.createRegistry(1099);

            //Register message object
            registry.rebind("Message", msgObject);

            System.out.println("Server starts....");
        } catch (RemoteException re) {
            re.printStackTrace();
        }
    }
}
