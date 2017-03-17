package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IMessage extends Remote {
    void captureMessage(byte[] message) throws RemoteException;
}
