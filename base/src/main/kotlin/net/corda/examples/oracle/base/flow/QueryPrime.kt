package net.corda.examples.oracle.base.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

// Simple flow which takes a reference to an oracle and a number then returns the corresponding nth prime number.
@InitiatingFlow
class QueryPrime(val oracle: Party, val n: Long) : FlowLogic<Int>() {
    @Suspendable override fun call(): Int {
        val session = initiateFlow(oracle)
        return session.sendAndReceive<Int>(n).unwrap { it }
    }
}