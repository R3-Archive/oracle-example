package net.corda.examples.oracle.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import net.corda.examples.oracle.api.ClientApi
import java.math.BigInteger
import java.util.function.Function

// CorDapp plugin registry class.
// Here we are registering some static web content and a web API.
// We also have to whitelist BigInteger as it's not on the default serialisation whitelist.
// From M12 onwards, flow whitelisting is handled by flow class annotations.
// This CorDapp doesn't require a service.
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
