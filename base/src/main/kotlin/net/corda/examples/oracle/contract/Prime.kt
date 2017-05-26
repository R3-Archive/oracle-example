package net.corda.examples.oracle.contract

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import java.math.BigInteger

// Contract and state object definition.
class Prime : Contract {
    // Legal prose reference.
    // A null hash is specified as we are don't need to implement this for the purposes of a demo.
    override val legalContractReference: SecureHash = SecureHash.zeroHash

    // State object with custom properties defined in the constructor.
    // If 'index' is a natural number N then 'value' is the Nth Prime.
    // Requester represents the Party that will store this fact (in the node vault).
    data class State(val index: Long,
                     val value: BigInteger,
                     val requester: AbstractParty) : ContractState {
        override val contract: Contract get() = Prime()
        override val participants: List<AbstractParty> get() = listOf(requester)
        override fun toString() = "The ${index}th prime number is $value."
    }

    // Command with data items.
    // Commands that are to be used in conjunction with an Oracle contain properties
    class Create(val index: Long, val value: BigInteger) : CommandData

    // Contract code.
    // Here, we are only checking that the properties in the state match those in the command.
    // We are relying on the Oracle to provide the correct nth prime.
    override fun verify(tx: TransactionForContract) = requireThat {
        val command = tx.commands.requireSingleCommand<Create>().value
        val output = tx.outputs.single() as State
        "The output prime is not correct." using (command.index == output.index && command.value == output.value)
    }
}
