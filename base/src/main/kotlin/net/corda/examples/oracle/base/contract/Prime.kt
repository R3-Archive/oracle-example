package net.corda.examples.oracle.base.contract

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.contextLogger

const val PRIME_PROGRAM_ID: ContractClassName = "net.corda.examples.oracle.base.contract.PrimeContract"

class PrimeContract : Contract {
    // Commands signed by oracles must contain the facts the oracle is attesting to.
    class Create(val n: Int, val nthPrime: Int) : CommandData

    // Our contract does not check that the Nth prime is correct. Instead, it checks that the
    // information in the command and state match.
    override fun verify(tx: LedgerTransaction) = requireThat {
        "There are no inputs" using (tx.inputs.isEmpty())
        val output = tx.outputsOfType<PrimeState>().single()
        val command = tx.commands.requireSingleCommand<Create>().value
        "The prime in the output does not match the prime in the command." using
                (command.n == output.n && command.nthPrime == output.nthPrime)
    }
}

// If 'n' is a natural number N then 'nthPrime' is the Nth prime.
// `Requester` is the Party that will store this fact in its vault.
data class PrimeState(val n: Int,
                      val nthPrime: Int,
                      val requester: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> get() = listOf(requester)
    override fun toString() = "The ${n}th prime number is $nthPrime."
}

data class NewPrimeState(val n: Int,
                         val nthPrime: Int,
                         val nLiteral: String,
                         val requester: AbstractParty): ContractState {
    override val participants: List<AbstractParty> get() = listOf(requester)
    override fun toString() = "The ${n}th prime number is $nLiteral."
}

class NewPrimeContract: UpgradedContract<PrimeState, NewPrimeState> {

    companion object {
        val logger = contextLogger()
    }

    // Commands signed by oracles must contain the facts the oracle is attesting to.
    class Create(val n: Int, val nthPrime: Int) : CommandData


    override val legacyContract: ContractClassName
        get() = "net.corda.examples.oracle.base.contract.PrimeContract"

    override fun verify(tx: LedgerTransaction) {
        "There are no inputs" using (tx.inputs.isEmpty())
        val output = tx.outputsOfType<PrimeState>().single()
        logger.info("MKIT - New and updated PrimeContract")
        val command = tx.commands.requireSingleCommand<PrimeContract.Create>().value
        "The prime in the output does not match the prime in the command." using
                (command.n == output.n && command.nthPrime == output.nthPrime)
    }

    override fun upgrade(state: PrimeState): NewPrimeState {
        val literal = when (state.nthPrime) {
            2 -> "Two"
            3 -> "Three"
            5 -> "Five"
            7 -> "Seven"
            11 -> "Eleven"
            13 -> "Thirteen"
            17 -> "Seventeen"
            19 -> "Nineteen"
            29 -> "Twenty Nine"
            else -> "Unknown"
        }
        logger.info("MKIT - converting state for the prime number: ${state.nthPrime}")
        return NewPrimeState(state.n, state.nthPrime, literal, state.requester)
    }
}