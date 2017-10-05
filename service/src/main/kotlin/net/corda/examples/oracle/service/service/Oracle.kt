package net.corda.examples.oracle.service.service

import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.examples.oracle.base.contract.Prime
import java.math.BigInteger

// We sub-class 'SingletonSerializeAsToken' to ensure that instances of this class are never serialised by Kryo.
// When a flow is check-pointed, the annotated @Suspendable methods and any object referenced from within those
// annotated methods are serialised onto the stack. Kryo, the reflection based serialisation framework we use, crawls
// the object graph and serialises anything it encounters, producing a graph of serialised objects.
// This can cause some issues, for example: we do not want to serialise large objects on to the stack or objects which
// may reference databases or other external services (which cannot be serialised!), therefore we mark certain objects
// with tokens. When Kryo encounters one of these tokens, it doesn't serialise the object, instead, it makes a
// reference to the type of the object. When flows are de-serialised, the token is used to connect up the object reference
// to an instance which should already exist on the stack.
@CordaService
class Oracle(val services: ServiceHub) : SingletonSerializeAsToken() {
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    // All the prime numbers, probably.
    // Generates a list of natural numbers and filters out the non-primes.
    // The reason why prime numbers were chosen is because they are easy to reason about and reduce the mental load
    // for this tutorial application.
    // Clearly, most developers can generate a list of primes and all but the largest prime numbers can be verified
    // deterministically in reasonable time. As such, it would be possible to add a constraint in the verify()
    // function that checks the nth prime is indeed the specified number.
    private val primes: Sequence<Int>
        get() = generateSequence(1) { it + 1 }.filter { BigInteger.valueOf(it.toLong()).isProbablePrime(16) }

    // Returns the nth prime for a given n > 0.
    fun query(n: Long): Int {
        require(n > 1) { "N must be greater than one." }
        return primes.take(n.toInt()).last()
    }

    // Signs over a transaction if the specified nth prime for a particular n is correct.
    // This function takes a filtered transaction which is a partial Merkle tree. Parts of the transaction which
    // the oracle doesn't need to see to opine over the correctness of the nth prime have been removed. In this case
    // all but the Prime.Create commands have been removed. If the nth prime is correct then the oracle signs over
    // the Merkle root (the hash) of the transaction.
    fun sign(ftx: FilteredTransaction): TransactionSignature {
        // Check the partial Merkle tree is valid.
        ftx.verify()

        // Check that the correct primes are present for the index values specified.
        fun commandValidator(elem: Command<*>): Boolean {
            // This oracle only cares about commands which have its public key in the signers list.
            // This oracle also only cares about Prime.Create commands.
            // Of course, some of these constraints can be easily amended. E.g. the oracle can sign over multiple
            // command types.
            if (!(myKey in elem.signers && elem.value is Prime.Create))
                throw IllegalArgumentException("Oracle received unknown command (not in signers or not Prime.Create).")
            val prime = elem.value as Prime.Create
            // This is where the check the validity of the nth prime.
            return query(prime.index) == prime.value
        }

        // This function is run for each non-hash leaf of the Merkle tree.
        // We only expect to see commands.
        fun check(elem: Any): Boolean {
            return when (elem) {
                is Command<*> -> commandValidator(elem)
                else -> throw IllegalArgumentException("Oracle received data of different type than expected.")
            }
        }

        // Validate the commands.
        if (!ftx.checkWithFun(::check)) throw IllegalArgumentException("Incorrect prime specified.")

        // Sign over the Merkle root and return the digital signature.
        return services.createSignature(ftx, myKey)
    }
}