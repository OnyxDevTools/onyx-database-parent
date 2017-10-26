package rmi

import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry

object RMIMessageServer {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            //Declare message object
            val msgObject = MessageImplementation()

            //Create and get reference from registry
            val registry = LocateRegistry.createRegistry(1099)

            //Register message object
            registry.rebind("Message", msgObject)

            println("Server starts....")
        } catch (re: RemoteException) {
            re.printStackTrace()
        }

    }
}
