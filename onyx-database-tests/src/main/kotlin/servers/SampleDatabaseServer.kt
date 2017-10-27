package servers

import com.onyx.application.impl.WebDatabaseServer
import com.onyx.persistence.IManagedEntity
import entities.SimpleEntity

/**
 * Created by timothy.osborn on 4/1/15.
 */
class SampleDatabaseServer(databaseLocation: String) : WebDatabaseServer(databaseLocation) {
    companion object {

        /**
         * Run Database Server
         *
         * ex:  executable /Database/Location/On/Disk 8080 admin admin
         *
         * @param args
         * @throws Exception
         */
        @Throws(Exception::class)
        @JvmStatic fun main(args: Array<String>) {
            val server1 = SampleDatabaseServer("C:/Sandbox/Onyx/Tests/server.oxd")
            server1.port = 8080
            server1.webServicePort = 8082
            server1.start()

            val simpleEntity = SimpleEntity()
            simpleEntity.name = "Test Name"
            simpleEntity.simpleId = "ASDF"
            server1.persistenceManager.saveEntity<IManagedEntity>(simpleEntity)

            server1.join()
            println("Started")
        }
    }

}
