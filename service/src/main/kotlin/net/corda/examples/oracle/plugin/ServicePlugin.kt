package net.corda.examples.oracle.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.SerializationCustomization
import net.corda.examples.oracle.service.Primes
import java.math.BigInteger
import java.util.function.Function

class ServicePlugin : CordaPluginRegistry() {
    override val servicePlugins: List<Function<PluginServiceHub, out Any>> = listOf(Function(Primes::Service))
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.addToWhitelist(BigInteger::class.java)
        return true
    }
}
