package net.corda.examples.oracle.contract

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import java.math.BigInteger

// Service type definition, required by both client and service.

// Contract.
class Prime : Contract {
    // Legal prose reference.
    override val legalContractReference: SecureHash = SecureHash.zeroHash

    // State object with custom properties defined in the constructor.
    data class State(val index: Long,
                     val value: BigInteger,
                     val requester: AbstractParty) : ContractState {
        override val contract: Contract get() = Prime()
        override val participants: List<AbstractParty> get() = listOf(requester)
        override fun toString() = "The ${index}th prime number is $value."
    }

    // Command with data items.
    class Create(val index: Long, val value: BigInteger) : CommandData

    // Contract code.
    override fun verify(tx: TransactionForContract) = requireThat {
        val command = tx.commands.requireSingleCommand<Create>().value
        val output = tx.outputs.single() as State
        "The output prime is not correct." using (command.index == output.index && command.value == output.value)
    }
}
