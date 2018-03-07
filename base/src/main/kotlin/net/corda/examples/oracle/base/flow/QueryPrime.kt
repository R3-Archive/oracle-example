package net.corda.examples.oracle.base.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

// Simple flow that requests the Nth prime number from the specified oracle.
@InitiatingFlow
class QueryPrime(val oracle: Party, val n: Int) : FlowLogic<Int>() {

    companion object {
        object INITIAL_CHECK: ProgressTracker.Step("Initial check.")
        object EXECUTE_FLOW: ProgressTracker.Step("Flow execution")
        const val HEARTBEAT: String = "Heartbeat"

        fun tracker() = ProgressTracker(INITIAL_CHECK, EXECUTE_FLOW)
    }

    override val progressTracker = tracker()

    @Suspendable override fun call() : Int {
        val session = initiateFlow(oracle)
        progressTracker.currentStep = INITIAL_CHECK
        logger.info("MKIT: - Sending HEARTBEAT")
        val checkResult = session.sendAndReceive<Boolean>(HEARTBEAT).unwrap { it }
        if (!checkResult) {
            throw FlowException("Unsuccessful heartbeat")
        }
        progressTracker.currentStep = EXECUTE_FLOW
        return session.sendAndReceive<Int>(n).unwrap { it }
    }
}