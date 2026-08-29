package com.onyx.network.rmi

import com.onyx.exception.MethodInvocationException
import com.onyx.network.auth.AuthenticationManager
import java.lang.reflect.UndeclaredThrowableException
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OnyxRMIServerInvocationFailureTest {

    @Test
    fun `remote invocation failure returns its underlying cause before request timeout`() {
        val server = OnyxRMIServer().apply {
            port = availablePort()
            register(AUTHENTICATION_SERVICE, PermitAllAuthentication, AuthenticationManager::class.java)
            register(FAILING_SERVICE, ThrowingService, FailingService::class.java)
            start()
        }
        val client = OnyxRMIClient().apply {
            requestTimeoutSeconds = 2
            authenticationManager = getRemoteObject(
                AUTHENTICATION_SERVICE,
                AuthenticationManager::class.java
            ) as AuthenticationManager
        }

        try {
            client.connect("127.0.0.1", server.port)
            val service = client.getRemoteObject(FAILING_SERVICE, FailingService::class.java) as FailingService

            val proxyFailure = assertFailsWith<UndeclaredThrowableException> {
                service.fail()
            }
            val failure = assertIs<MethodInvocationException>(proxyFailure.undeclaredThrowable)

            assertIs<NullPointerException>(failure.cause)
        } finally {
            client.close()
            server.stop()
        }
    }

    private var OnyxRMIClient.requestTimeoutSeconds: Int
        get() = error("Write-only test property")
        set(value) {
            OnyxRMIClient::class.java.getDeclaredField("timeout").apply {
                isAccessible = true
                setInt(this@requestTimeoutSeconds, value)
            }
        }

    private fun availablePort(): Int = ServerSocket(0).use { it.localPort }

    private interface FailingService {
        fun fail(): String
    }

    private object ThrowingService : FailingService {
        override fun fail(): String = throw NullPointerException("missing schema context")
    }

    private object PermitAllAuthentication : AuthenticationManager {
        override fun verify(username: String, password: String) = Unit
    }

    private companion object {
        const val AUTHENTICATION_SERVICE = "test-authentication"
        const val FAILING_SERVICE = "test-failure"
    }
}
