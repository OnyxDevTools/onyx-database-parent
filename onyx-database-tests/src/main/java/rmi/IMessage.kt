package rmi

import java.rmi.Remote
import java.rmi.RemoteException

interface IMessage : Remote {
    @Throws(RemoteException::class)
    fun captureMessage(message: ByteArray)
}
