package rmi

import java.rmi.NotBoundException
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry

object RMIClient {

    //Lookup message object from server
    val message: IMessage?
        get() {
            try {
                val registry = LocateRegistry.getRegistry("localhost", 1099)
                return registry.lookup("Message") as IMessage

            } catch (nbe: NotBoundException) {
                nbe.printStackTrace()
            } catch (nbe: RemoteException) {
                nbe.printStackTrace()
            }

            return null
        }
}