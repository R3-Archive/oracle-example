package net.corda.examples.oracle

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.SerializationCustomization
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.math.BigInteger
import java.security.PublicKey
import java.util.function.Function

// Contract.
class Prime : Contract {
    // Legal prose reference.
    override val legalContractReference: SecureHash = SecureHash.zeroHash

    // Command with data items.
    class Create(val index: Long, val value: BigInteger) : CommandData

    // State definition.
    data class State(val index: Long,
                     val value: BigInteger,
                     val requester: AbstractParty) : ContractState {
        override val contract: Contract get() = Prime()
        override val participants: List<AbstractParty> get() = listOf(requester)
        override fun toString() = "The ${index}th prime number is $value."
    }

    // Contract code.
    override fun verify(tx: TransactionForContract) = requireThat {
        val command = tx.commands.requireSingleCommand<Create>().value
        val output = tx.outputs.single() as Prime.State
        "The output prime is not correct." using (command.index == output.index && command.value == output.value)
    }
}

// Plugin.
class PrimesServicePlugin : CordaPluginRegistry() {
    override val servicePlugins: List<Function<PluginServiceHub, out Any>> = listOf(Function(PrimesOracle::Service))
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        custom.addToWhitelist(BigInteger::class.java)
        return true
    }
}

object PrimesOracle {
    val type = ServiceType.corda.getSubType("primes_oracle")

    class Service(val services: PluginServiceHub) : SingletonSerializeAsToken() {
        private val type = ServiceType.corda.getSubType("primes_oracle")

        private val primesOracle by lazy {
            val myNodeInfo = services.myInfo
            val myIdentity = myNodeInfo.serviceIdentities(type).first()
            // Why this?
            val mySigningKey = myIdentity.owningKey
            PrimesOracle(myIdentity, mySigningKey, services)
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
    class PrimesOracle(val identity: Party, val signingKey: PublicKey, val services: ServiceHub) {
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