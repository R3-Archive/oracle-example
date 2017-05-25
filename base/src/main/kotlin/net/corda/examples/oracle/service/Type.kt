package net.corda.examples.oracle.service

import net.corda.core.node.services.ServiceType

object PrimeType {
    val type = ServiceType.getServiceType("net.corda.examples", "primes_oracle")
}
