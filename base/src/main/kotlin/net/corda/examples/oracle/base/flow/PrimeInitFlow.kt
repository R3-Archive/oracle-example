package net.corda.examples.oracle.base.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.examples.oracle.base.contract.NewPrimeContract
import net.corda.examples.oracle.base.contract.PrimeState

// Simple flow that requests the Nth prime number from the specified oracle.
@StartableByRPC
class PrimeInitFlow : FlowLogic<Unit>() {

    override val progressTracker = QueryPrime.tracker()

    @Suspendable override fun call() {
        val oldStates = serviceHub.vaultService.queryBy(PrimeState::class.java).states
        oldStates.forEach {
            subFlow(ContractUpgradeFlow.Initiate(it, NewPrimeContract::class.java))
        }
    }
}