package net.corda.examples.oracle.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import net.corda.examples.oracle.api.ClientApi
import java.math.BigInteger
import java.util.function.Function

class ClientPlugin : CordaPluginRegistry() {
    override val webApis = listOf(Function(::ClientApi))

    override val staticServeDirs: Map<String, String> = mapOf(
            "primes" to javaClass.classLoader.getResource("primesWeb").toExternalForm()
    )

    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.addToWhitelist(BigInteger::class.java)
        return true
    }
}
