package net.corda.examples.oracle

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.serialization.SerializationCustomization
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.FinalityFlow
import java.math.BigInteger
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

class PrimesClientPlugin : CordaPluginRegistry() {
    override val webApis = listOf(Function(::PrimesApi))
    //    override val staticServeDirs: Map<String, String> = mapOf(
//            "primes" to javaClass.classLoader.getResource("primesweb").toExternalForm()
//    )
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.addToWhitelist(BigInteger::class.java)
        return true
    }
}

@Path("primes")
class PrimesApi(val services: CordaRPCOps) {
    @GET
    @Path("primes")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok(mapOf("message" to "Template GET endpoint.")).build()
    }
}

@InitiatingFlow
class CreatePrime(val index: Long) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Get references to all required parties.
        val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity
        val oracle = serviceHub.networkMapCache.getNodesWithService(PrimesOracle.type).single()
        val oracleService = oracle.serviceIdentities(PrimesOracle.type).single()
        val me = serviceHub.myInfo.legalIdentity

        // Query the Oracle to get specified nth prime number.
        val nthPrime = subFlow(QueryPrime(oracle.legalIdentity, index))

        // Create a new transaction using the data from the Oracle.
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
        val oracleSignature = subFlow(SignPrime(oracle.legalIdentity, ftx))
        val ptx = builder.addSignatureUnchecked(oracleSignature).toSignedTransaction(false)

        // Add our signature.
        val mySignature = serviceHub.createSignature(ptx, me.owningKey)
        val stx = ptx + mySignature

        // Finalise.
        val result = subFlow(FinalityFlow(stx)).single()

        return result
    }
}

@InitiatingFlow
class QueryPrime(val oracle: Party, val query: Long) : FlowLogic<BigInteger>() {
    @Suspendable override fun call() = sendAndReceive<BigInteger>(oracle, query).unwrap { it }
}

@InitiatingFlow
class SignPrime(val oracle: Party, val ftx: FilteredTransaction) : FlowLogic<DigitalSignature.LegallyIdentifiable>() {
    @Suspendable override fun call() = sendAndReceive<DigitalSignature.LegallyIdentifiable>(oracle, ftx).unwrap { it }
}