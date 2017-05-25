package net.corda.examples.oracle.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap
import java.math.BigInteger

@InitiatingFlow
class QueryPrime(val oracle: Party, val query: Long) : FlowLogic<BigInteger>() {
    @Suspendable override fun call() = sendAndReceive<BigInteger>(oracle, query).unwrap { it }
}

