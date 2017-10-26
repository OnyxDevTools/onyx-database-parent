package rmi


import java.rmi.RemoteException
import java.rmi.server.UnicastRemoteObject

class MessageImplementation @Throws(RemoteException::class)
constructor() : UnicastRemoteObject(), IMessage {

    override fun captureMessage(message: ByteArray) {

    }
}
