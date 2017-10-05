package net.corda.examples.oracle.service.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.examples.oracle.base.flow.QueryPrime
import net.corda.examples.oracle.service.service.Oracle

// The Service side flow to handle oracle queries.
@InitiatedBy(QueryPrime::class)
class QueryHandler(val session: FlowSession) : FlowLogic<Unit>() {
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
        val request = session.receive<Long>().unwrap { it }
        progressTracker.currentStep = SENDING
        try {
            // Get the nth prime from the oracle.
            val response = serviceHub.cordaService(Oracle::class.java).query(request)
            // Send back the result.
            session.send(response)
        } catch (e: Exception) {
            // Re-throw exceptions as Flow Exceptions so they are propagated to other nodes.
            throw FlowException(e)
        }
    }
}