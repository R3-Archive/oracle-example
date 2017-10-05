package net.corda.examples.oracle.base.contract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

const val PRIME_PROGRAM_ID: ContractClassName = "net.corda.examples.oracle.base.contract.Prime"

// Contract and state object definition.
class Prime : Contract {

    // State object with custom properties defined in the constructor.
    // If 'index' is a natural number N then 'value' is the Nth prime.
    // Requester represents the Party that will store this fact (in the node vault).
    data class State(val index: Long,
                     val value: Int,
                     val requester: AbstractParty) : ContractState {
        override val participants: List<AbstractParty> get() = listOf(requester)
        override fun toString() = "The ${index}th prime number is $value."
    }

    // Command with data items.
    // Commands that are to be used in conjunction with an oracle contain properties.
    class Create(val index: Long, val value: Int) : CommandData

    // Contract code.
    // Here, we are only checking that the properties in the state match those in the command.
    // We are relying on the oracle to provide the correct nth prime.
    override fun verify(tx: LedgerTransaction) = requireThat {
        val command = tx.commands.requireSingleCommand<Create>().value
        val output = tx.outputsOfType<State>().single()
        "The output prime is not correct." using (command.index == output.index && command.value == output.value)
    }
}