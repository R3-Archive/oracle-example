package net.corda.examples.oracle.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import java.math.BigInteger

// This plugin adds a reference to the service which hosts the Primes Oracle.
// We also add BigInteger to the serialisation whitelist as it's not added by default.
class ServicePlugin : CordaPluginRegistry() {
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.addToWhitelist(BigInteger::class.java)
        return true
    }
}
