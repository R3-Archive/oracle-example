package net.corda.examples.oracle.client

import net.corda.core.utilities.getOrThrow
import net.corda.examples.oracle.base.contract.Prime
import net.corda.examples.oracle.client.flow.CreatePrime
import net.corda.examples.oracle.service.flow.QueryHandler
import net.corda.examples.oracle.service.flow.SignHandler
import net.corda.examples.oracle.service.service.Oracle
import net.corda.node.internal.StartedNode
import net.corda.node.utilities.configureDatabase
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PrimesClientTests {
    private lateinit var mockNet: MockNetwork
    private lateinit var a: StartedNode<MockNode>
    private lateinit var oracle: StartedNode<MockNode>

    @Before
    fun setUp() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes(2)
        a = nodes.partyNodes[0]
        oracle = nodes.partyNodes[1]
        val database = configureDatabase(MockServices.makeTestDataSourceProperties(), MockServices.makeTestDatabaseProperties(), createIdentityService = MockServices.Companion::makeTestIdentityService)
        database.transaction {
            oracle.registerInitiatedFlow(QueryHandler::class.java)
            oracle.registerInitiatedFlow(SignHandler::class.java)
            oracle.internals.installCordaService(Oracle::class.java)
        }
        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    @Test
    fun `oracle test`() {
        val flow = a.services.startFlow(CreatePrime(100))
        mockNet.runNetwork()
        val result = flow.resultFuture.getOrThrow().tx.outputs.single().data as Prime.State
        assertEquals("The 100th prime number is 541.", result.toString())
        println(result)
    }

}
