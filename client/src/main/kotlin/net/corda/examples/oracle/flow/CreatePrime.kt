package net.corda.examples.oracle.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.examples.oracle.contract.Prime
import net.corda.examples.oracle.service.PrimeType
import net.corda.flows.FinalityFlow

@InitiatingFlow
@StartableByRPC
class CreatePrime(val index: Long) : FlowLogic<SignedTransaction>() {

    companion object {
        object INITIALISING : ProgressTracker.Step("Initialising flow.")
        object QUERYING : ProgressTracker.Step("Querying Oracle for an nth prime.")
        object BUILDING_AND_VERIFYING : ProgressTracker.Step("Building and verifying transaction.")
        object ORACLE_SIGNING : ProgressTracker.Step("Requesting Oracle signature.")
        object SIGNING : ProgressTracker.Step("signing transaction.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, QUERYING, BUILDING_AND_VERIFYING, ORACLE_SIGNING, SIGNING, FINALISING)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Get references to all required parties.
        progressTracker.currentStep = INITIALISING
        val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity
        val oracle = serviceHub.networkMapCache.getNodesWithService(PrimeType.type).single()
        val oracleService = oracle.serviceIdentities(PrimeType.type).single()
        val me = serviceHub.myInfo.legalIdentity

        // Query the Oracle to get specified nth prime number.
        progressTracker.currentStep = QUERYING
        val nthPrime = subFlow(QueryPrime(oracle.legalIdentity, index))

        // Create a new transaction using the data from the Oracle.
        progressTracker.currentStep = BUILDING_AND_VERIFYING
        val command = Command(Prime.Create(index, nthPrime), listOf(oracleService.owningKey, me.owningKey))
        val state = Prime.State(index, nthPrime, me)
        val builder = TransactionType.General.Builder(notary).withItems(command, state)

        // Verify the transaction.
        builder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

        // Build a filtered transaction for the Oracle to sign over.
        val ftx = builder.toWireTransaction().buildFilteredTransaction {
            when (it) {
                is Command -> oracleService.owningKey in it.signers && it.value is Prime.Create
                else -> false
            }
        }

        // Get a signature from the Oracle and add it to the transaction.
        progressTracker.currentStep = ORACLE_SIGNING
        val oracleSignature = subFlow(SignPrime(oracle.legalIdentity, ftx))
        val ptx = builder.addSignatureUnchecked(oracleSignature).toSignedTransaction(false)

        // Add our signature.
        progressTracker.currentStep = SIGNING
        val mySignature = serviceHub.createSignature(ptx, me.owningKey)
        val stx = ptx + mySignature

        // Finalise.
        progressTracker.currentStep = FINALISING
        val result = subFlow(FinalityFlow(stx)).single()

        return result
    }
}


