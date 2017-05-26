package net.corda.examples.oracle.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap
import java.math.BigInteger

// Simple flow which takes a reference to an Oracle and a number then returns the corresponding nth prime number.
@InitiatingFlow
class QueryPrime(val oracle: Party, val n: Long) : FlowLogic<BigInteger>() {
    @Suspendable override fun call() = sendAndReceive<BigInteger>(oracle, n).unwrap { it }
}

