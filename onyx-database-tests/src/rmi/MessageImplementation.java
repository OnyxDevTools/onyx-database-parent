package rmi;


import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class MessageImplementation extends UnicastRemoteObject
        implements IMessage {
    public MessageImplementation() throws RemoteException {

    }

    public void captureMessage(byte[] message) {

    }
}
