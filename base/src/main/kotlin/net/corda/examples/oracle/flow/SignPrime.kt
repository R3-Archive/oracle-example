package net.corda.examples.oracle.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap

@InitiatingFlow
class SignPrime(val oracle: Party, val ftx: FilteredTransaction) : FlowLogic<DigitalSignature.LegallyIdentifiable>() {
    @Suspendable override fun call() = sendAndReceive<DigitalSignature.LegallyIdentifiable>(oracle, ftx).unwrap { it }
}
