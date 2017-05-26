package net.corda.examples.oracle.api

import net.corda.client.rpc.notUsed
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.examples.oracle.contract.Prime
import net.corda.examples.oracle.flow.CreatePrime
import org.bouncycastle.asn1.x500.X500Name
import rx.Observable
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("primes")
class ClientApi(val services: CordaRPCOps) {
    private val myLegalName: X500Name = services.nodeIdentity().legalIdentity.name

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<String>> {
        val peers = services.networkMapUpdates()
                .justSnapshot
                .map { it.legalIdentity.name.toString() }
        return mapOf("peers" to peers)
    }

    /**
     * Enumerates all the prime numbers we currently have in the vault.
     */
    @GET
    @Path("primes")
    @Produces(MediaType.APPLICATION_JSON)
    fun primes(): List<StateAndRef<ContractState>> {
        return services.vaultAndUpdates().justSnapshot.filter { it.state.data is Prime.State }
    }

    /**
     * Creates a new prime number by consulting the primes Oracle.
     */
    @GET
    @Path("create-prime")
    @Produces(MediaType.APPLICATION_JSON)
    fun createPrime(@QueryParam(value = "n") n: Long): Response {
        // Start the CretePrime flow. We block and wait for the flow to return.
        val (status, message) = try {
            val flowHandle = services.startFlowDynamic(CreatePrime::class.java, n)
            val result = flowHandle.use { it.returnValue.getOrThrow() }.tx.outputs.single().data as Prime.State
            // Return the response.
            Response.Status.CREATED to "$result"
        } catch (e: Exception) {
            // For the purposes of this demo app, we do not differentiate by exception type.
            Response.Status.BAD_REQUEST to e.message
        }

        return Response.status(status).entity(message).build()
    }


    // Helper method to get just the snapshot portion of an RPC call which also returns an Observable of updates. It's
    // important to unsubscribe from this Observable if we're not going to use it as otherwise we leak resources on the server.
    private val <A> Pair<A, Observable<*>>.justSnapshot: A get() {
        second.notUsed()
        return first
    }
}
