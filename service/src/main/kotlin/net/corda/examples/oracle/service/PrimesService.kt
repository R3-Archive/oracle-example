package net.corda.examples.oracle.service

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.examples.oracle.contract.Prime
import net.corda.examples.oracle.flow.QueryPrime
import net.corda.examples.oracle.flow.SignPrime
import java.math.BigInteger
import java.security.PublicKey

object Primes {

    // We sub-class 'SingletonSerializeAsToken' to ensure that instances of this class are never serialised by Kryo.
    // When a flow is check-pointed, the annotated @Suspendable methods and any object referenced from within those
    // annotated methods are serialised. Kryo, the reflection based serialisation framework we use, crawls
    // the object graph and serialises anything it encounters, producing a graph of serialised objects.
    // This can cause some issues, for example: we do not want to serialise large objects on to the stack or objects which
    // may reference databases or other external services (which cannot be serialised!), therefore we mark certain objects
    // with tokens. When Kryo encounters one of these tokens, it doesn't serialise the object, instead, it makes a
    // reference to the type of the object. When flows are de-serialised, the token is used to connect up the object reference
    // to an instance which should already exist on the stack.
    class Service(val services: PluginServiceHub) : SingletonSerializeAsToken() {

        // An instance of the Oracle cannot be initialised immediately as the network map would not have been
        // populated at this point. This is an artifact of Corda node's start-up sequence: the ServiceHub is initialised
        // and then the network map cache is populated by querying the network map service and receiving the responses.
        // As such we need to the primesOracle reference to be a lazy property, such that it is initialised only when
        // first referenced by the QueryHandler or SignHandler.
        private val primesOracle by lazy {
            val myNodeInfo = services.myInfo
            val myServiceIdentity = myNodeInfo.serviceIdentities(PrimeType.type).first()
            val mySigningKey = myServiceIdentity.owningKey
            Oracle(myServiceIdentity, mySigningKey, services)
        }

        // Register the flow handlers for clients querying the Oracle and asking for signatures over a transaction.
        // We pass the Flows an instance of this service (which contains a reference to the Oracle).
        init {
            services.registerServiceFlow(QueryPrime::class.java) { QueryHandler(it, this) }
            services.registerServiceFlow(SignPrime::class.java) { SignHandler(it, this) }
        }

        // The Service side flow to handle Oracle queries.
        class QueryHandler(val otherParty: Party, val services: Service) : FlowLogic<Unit>() {
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
                    val response = services.primesOracle.query(request)
                    // Send back the result.
                    send(otherParty, response)
                } catch (e: Exception) {
                    // Re-throw exceptions as Flow Exceptions so they are propagated to other nodes.
                    throw FlowException(e)
                }
            }
        }

        // As above, however this flow deals with the signing of a transaction.
        class SignHandler(val otherParty: Party, val services: Service) : FlowLogic<Unit>() {
            companion object {
                object RECEIVED : ProgressTracker.Step("Received sign request")
                object SENDING : ProgressTracker.Step("Sending sign response")
            }

            override val progressTracker = ProgressTracker(RECEIVED, SENDING)

            init {
                progressTracker.currentStep = RECEIVED
            }

            @Suspendable
            override fun call() {
                val request = receive<FilteredTransaction>(otherParty).unwrap { it }
                progressTracker.currentStep = SENDING
                try {
                    val response = services.primesOracle.sign(request)
                    send(otherParty, response)
                } catch (e: Exception) {
                    throw FlowException(e)
                }
            }
        }
    }

    // The Oracle.
    class Oracle(val identity: Party, val signingKey: PublicKey, val services: ServiceHub) {
        // All the prime numbers, probably.
        // Generates a list of natural numbers and filters out the non-primes.
        // The reason why prime numbers were chosen is because they are easy to reason about and reduce the mental load
        // for this tutorial application.
        // Clearly, most developers can generate a list of primes and all but the largest prime numbers can be verified
        // deterministically in reasonable time. As such, it would be possible to add a constraint in the verify()
        // function that checks the nth prime is indeed the specified number.
        private val primes: Sequence<BigInteger>
            get() = generateSequence(BigInteger.ONE) { it + BigInteger.ONE }.filter { it.isProbablePrime(16) }

        // Returns the nth prime for a given n > 0.
        fun query(n: Long): BigInteger {
            require(n > 1) { "N must be greater than one." }
            return primes.take(n.toInt()).last()
        }

        // Signs over a transaction if the specified nth prime for a particular n is correct.
        // This function takes a filtered transaction which is a partial Merkle tree. Parts of the transaction which
        // the Oracle doesn't need to see to opine over the correctness of the nth prime have been removed. In this case
        // all but the Prime.Create commands have been removed. If the nth prime is correct then the Oracle signs over
        // the Merkle root (the hash) of the transaction.
        fun sign(ftx: FilteredTransaction): DigitalSignature.LegallyIdentifiable {
            // Check the partial Merkle tree is valid.
            if (!ftx.verify()) throw MerkleTreeException("Couldn't verify partial Merkle tree.")

            // Check that the correct primes are present for the index values specified.
            fun commandValidator(elem: Command): Boolean {
                // This Oracle only cares about commands which have its public key in the signers list.
                // This Oracle also only cares about Prime.Create commands.
                // Of course, some of these constraints can be easily amended. E.g. they Oracle can sign over multiple
                // command types.
                if (!(identity.owningKey in elem.signers && elem.value is Prime.Create))
                    throw IllegalArgumentException("Oracle received unknown command (not in signers or not Prime.Create).")
                val prime = elem.value as Prime.Create
                // This is where the check the validity of the nth prime.
                return query(prime.index) == prime.value
            }

            // This function is run for each non-hash leaf of the Merkle tree.
            // We only expect to see commands.
            fun check(elem: Any): Boolean {
                return when (elem) {
                    is Command -> commandValidator(elem)
                    else -> throw IllegalArgumentException("Oracle received data of different type than expected.")
                }
            }

            // Validate the commands.
            val leaves = ftx.filteredLeaves
            if (!leaves.checkWithFun(::check)) throw IllegalArgumentException()

            // Sign over the Merkle root and return the digital signature.
            val signature = services.keyManagementService.sign(ftx.rootHash.bytes, signingKey)
            return DigitalSignature.LegallyIdentifiable(identity, signature.bytes)
        }
    }
}
