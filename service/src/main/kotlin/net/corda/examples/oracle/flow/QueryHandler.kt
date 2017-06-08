package net.corda.examples.oracle.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.examples.oracle.service.Oracle

// The Service side flow to handle Oracle queries.
@InitiatedBy(QueryPrime::class)
class QueryHandler(val otherParty: Party) : FlowLogic<Unit>() {
    companion object {
        object RECEIVED : ProgressTracker.Step("Received query request")
        object SENDING : ProgressTracker.Step("Sending query response")
    }

    override val progressTracker = ProgressTracker(RECEIVED, SENDING)

    init {
        progressTracker.currentStep = RECEIVED
    }

    @Suspendable
    override fun call() {
        // Receive the request.
        val request = receive<Long>(otherParty).unwrap { it }
        progressTracker.currentStep = SENDING
        try {
            // Get the nth prime from the Oracle.
            val response = serviceHub.cordaService(Oracle::class.java).query(request)
            // Send back the result.
            send(otherParty, response)
        } catch (e: Exception) {
            // Re-throw exceptions as Flow Exceptions so they are propagated to other nodes.
            throw FlowException(e)
        }
    }
}
