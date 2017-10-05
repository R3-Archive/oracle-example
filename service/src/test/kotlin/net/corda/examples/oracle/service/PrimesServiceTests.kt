package net.corda.examples.oracle.service

import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.transactions.TransactionBuilder
import net.corda.examples.oracle.base.contract.PRIME_PROGRAM_ID
import net.corda.examples.oracle.base.contract.Prime
import net.corda.examples.oracle.service.service.Oracle
import net.corda.testing.*
import net.corda.testing.node.MockServices
import org.junit.Test
import java.util.function.Predicate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrimesServiceTests : TestDependencyInjectionBase() {
    private val dummyServices = MockServices(listOf("net.corda.examples.oracle.base.contract"), CHARLIE_KEY)
    private val oracle = Oracle(dummyServices)

    @Test
    fun `successful query`() {
        assertEquals(104729, oracle.query(10000))
    }

    @Test
    fun `bad query parameter`() {
        assertFailsWith<IllegalArgumentException> { oracle.query(0) }
        assertFailsWith<IllegalArgumentException> { oracle.query(-1) }
    }

    @Test
    fun `successful sign`() {
        val command = Command(Prime.Create(10, 29), listOf(CHARLIE.owningKey))
        val state = Prime.State(10, 29, ALICE)
        val stateAndContract = StateAndContract(state, PRIME_PROGRAM_ID)
        val ftx = TransactionBuilder(DUMMY_NOTARY)
                .withItems(stateAndContract, command)
                .toWireTransaction(dummyServices)
                .buildFilteredTransaction(Predicate {
                    when (it) {
                        is Command<*> -> oracle.services.myInfo.legalIdentities.first().owningKey in it.signers && it.value is Prime.Create
                        else -> false
                    }
                })
        val signature = oracle.sign(ftx)
        assert(signature.verify(ftx.id))
    }

    @Test
    fun `incorrect prime specified`() {
        val command = Command(Prime.Create(10, 1000), listOf(CHARLIE.owningKey))
        val state = Prime.State(10, 29, ALICE)
        val stateAndContract = StateAndContract(state, PRIME_PROGRAM_ID)
        val ftx = TransactionBuilder(DUMMY_NOTARY)
                .withItems(stateAndContract, command)
                .toWireTransaction(oracle.services)
                .buildFilteredTransaction(Predicate {
                    when (it) {
                        is Command<*> -> oracle.services.myInfo.legalIdentities.first().owningKey in it.signers && it.value is Prime.Create
                        else -> false
                    }
                })
        assertFailsWith<IllegalArgumentException>("Incorrect prime specified.") { oracle.sign(ftx) }
    }
}