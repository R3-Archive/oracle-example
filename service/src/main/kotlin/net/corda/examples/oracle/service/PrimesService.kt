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
    class Service(val services: PluginServiceHub) : SingletonSerializeAsToken() {

        private val primesOracle by lazy {
            val myNodeInfo = services.myInfo
            val myServiceIdentity = myNodeInfo.serviceIdentities(PrimeType.type).first()
            val mySigningKey = myServiceIdentity.owningKey
            Oracle(myServiceIdentity, mySigningKey, services)
        }

        init {
            services.registerServiceFlow(QueryPrime::class.java) { QueryHandler(it, this) }
            services.registerServiceFlow(SignPrime::class.java) { SignHandler(it, this) }
        }

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
                val request = receive<Long>(otherParty).unwrap { it }
                progressTracker.currentStep = SENDING
                val response = services.primesOracle.query(request)
                send(otherParty, response)
            }
        }

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

    // Oracle.
    class Oracle(val identity: Party, val signingKey: PublicKey, val services: ServiceHub) {
        // All the prime numbers, probably.
        // Generates a list of natural numbers and filters out the non-primes.
        // TODO: Memoize this.
        private val primes: Sequence<BigInteger>
            get() = generateSequence(BigInteger.ONE) { it + BigInteger.ONE }.filter { it.isProbablePrime(16) }

        fun query(n: Long): BigInteger {
            require(n > 1) { "N must be greater than one." }
            return primes.take(n.toInt()).last()
        }

        fun sign(ftx: FilteredTransaction): DigitalSignature.LegallyIdentifiable {
            // Check the partial Merkle tree is valid.
            if (!ftx.verify()) throw MerkleTreeException("Couldn't verify partial Merkle tree.")

            // Check that the correct primes are present for the index values specified.
            fun commandValidator(elem: Command): Boolean {
                if (!(identity.owningKey in elem.signers && elem.value is Prime.Create))
                    throw IllegalArgumentException("Oracle received unknown command (not in signers or not Prime.Create).")
                val prime = elem.value as Prime.Create
                return query(prime.index) == prime.value
            }

            // We only want to see commands.
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
