package net.corda.examples.oracle

import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.CHARLIE
import net.corda.core.utilities.CHARLIE_KEY
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.node.utilities.configureDatabase
import net.corda.node.utilities.transaction
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrimesServiceTests {
    val dummyServices = MockServices(CHARLIE_KEY)
    lateinit var oracle: PrimesOracle.PrimesOracle
    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        // Mock components for testing the Oracle.
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
        database.transaction {
            oracle = PrimesOracle.PrimesOracle(CHARLIE, CHARLIE.owningKey, dummyServices)
        }
    }

    @After
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `successful query`() {
        database.transaction {
            val result = oracle.query(10000)
            assertEquals("104729", result.toString())
        }
    }

    @Test
    fun `bad query parameter`() {
        database.transaction {
            assertFailsWith<IllegalArgumentException> { oracle.query(0) }
            assertFailsWith<IllegalArgumentException> { oracle.query(-1) }
        }
    }

    @Test
    fun `successful sign`() {
        database.transaction {
            val command = Command(Prime.Create(10, BigInteger.valueOf(29)), listOf(CHARLIE.owningKey))
            val state = Prime.State(10, BigInteger.valueOf(29), ALICE)
            val wtx: WireTransaction = TransactionType.General.Builder(DUMMY_NOTARY)
                    .withItems(state, command)
                    .toWireTransaction()
            val ftx: FilteredTransaction = wtx.buildFilteredTransaction {
                when (it) {
                    is Command -> oracle.signingKey in it.signers && it.value is Prime.Create
                    else -> false
                }
            }
            val signature = oracle.sign(ftx)
            assert(signature.verify(ftx.rootHash.bytes))
        }
    }

    @Test
    fun `incorrect prime specified`() {
        database.transaction {
            val command = Command(Prime.Create(10, BigInteger.valueOf(1000)), listOf(CHARLIE.owningKey))
            val state = Prime.State(10, BigInteger.valueOf(29), ALICE)
            val wtx: WireTransaction = TransactionType.General.Builder(DUMMY_NOTARY).withItems(state, command).toWireTransaction()
            val ftx: FilteredTransaction = wtx.buildFilteredTransaction {
                when (it) {
                    is Command -> oracle.signingKey in it.signers && it.value is Prime.Create
                    else -> false
                }
            }
            assertFailsWith<IllegalArgumentException> { oracle.sign(ftx) }
        }
    }
}
