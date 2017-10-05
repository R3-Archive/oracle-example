package net.corda.examples.oracle.client.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.examples.oracle.base.contract.Prime
import net.corda.examples.oracle.base.flow.QueryPrime
import net.corda.examples.oracle.base.flow.SignPrime
import java.util.function.Predicate

// This is the client side flow that makes use of the 'QueryPrime' and 'SignPrime' flows to obtain data from the Oracle
// and the Oracle's signature over the transaction containing it.
@InitiatingFlow     // This flow can be started by the node.
@StartableByRPC // Annotation to allow this flow to be started via RPC.
class CreatePrime(val index: Long) : FlowLogic<SignedTransaction>() {
    // Progress tracker boilerplate.
    companion object {
        object INITIALISING : ProgressTracker.Step("Initialising flow.")
        object QUERYING : ProgressTracker.Step("Querying Oracle for an nth prime.")
        object BUILDING : ProgressTracker.Step("Building transaction.")
        object VERIFYING : ProgressTracker.Step("Verifying transaction.")
        object ORACLE_SIGNING : ProgressTracker.Step("Requesting Oracle signature.")
        object SIGNING : ProgressTracker.Step("signing transaction.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, QUERYING, BUILDING, VERIFYING, ORACLE_SIGNING, SIGNING, FINALISING)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Get references to all required parties.
        progressTracker.currentStep = INITIALISING
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        // We get the oracle reference by using the ServiceType definition defined in the base CorDapp.
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(CordaX500Name("Oracle", "New York","US"))?.legalIdentities?.first()
                ?: throw IllegalArgumentException("Requested oracle ('O=Oracle,L=New York,C=US') not found on network.")

        // Query the Oracle to get specified nth prime numberz`.
        progressTracker.currentStep = QUERYING
        // Query the Oracle. Specify the identity of the Oracle we want to query and a natural number N.
        val nthPrime = subFlow(QueryPrime(oracle, index))

        // Create a new transaction using the data from the Oracle.
        progressTracker.currentStep = BUILDING
        // Build our command.
        // NOTE: The command requires the public key of the oracle, hence we need the signature from the oracle over
        // this transaction.
        val command = Command(Prime.Create(index, nthPrime), listOf(oracle.owningKey, ourIdentity.owningKey))
        // Create a new prime state.
        val state = Prime.State(index, nthPrime, ourIdentity)
        // Add the state and the command to the builder.
        val builder = TransactionBuilder(notary).withItems(command, state)

        progressTracker.currentStep = VERIFYING
        // Verify the transaction.
        builder.verify(serviceHub)

        // Add our signature.
        progressTracker.currentStep = SIGNING
        val ptx = serviceHub.signInitialTransaction(builder)

        // Get a signature from the Oracle and add it to the transaction.
        progressTracker.currentStep = ORACLE_SIGNING

        // Build a filtered transaction for the Oracle to sign over.
        // We only want to expose 'Prime.Create' commands if the specified Oracle is a signer.
        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is Command<*> -> oracle.owningKey in it.signers && it.value is Prime.Create
                else -> false
            }
        })

        // Get a signature from the Oracle over the Merkle root of the transaction.
        val oracleSignature = subFlow(SignPrime(oracle, ftx))
        // Append the oracle's signature to the transaction and convert the builder to a SignedTransaction.
        // We use the 'checkSufficientSignatures = false' as we haven't collected all the signatures yet.
        val stx = ptx.withAdditionalSignature(oracleSignature)

        // Finalise.
        // We do this by calling finality flow. The transaction will be broadcast to all parties listed in 'participants'.
        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(stx))
    }
}