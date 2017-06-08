package net.corda.examples.oracle

import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceInfo
import net.corda.examples.oracle.contract.Prime
import net.corda.examples.oracle.flow.CreatePrime
import net.corda.examples.oracle.flow.QueryHandler
import net.corda.examples.oracle.flow.SignHandler
import net.corda.examples.oracle.service.Oracle
import net.corda.examples.oracle.service.PrimeType
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class PrimesClientTests {
    lateinit var mockNet: MockNetwork
    lateinit var notary: Party
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var oracle: MockNetwork.MockNode

    @Before
    fun setUp() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes(2)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        notary = nodes.notaryNode.info.notaryIdentity
        val serviceInfo = ServiceInfo(PrimeType.type)
        oracle = mockNet.createNode(nodes.mapNode.info.address, advertisedServices = serviceInfo)
        oracle.installCordaService(Oracle::class.java)
        oracle.registerInitiatedFlow(QueryHandler::class.java)
        oracle.registerInitiatedFlow(SignHandler::class.java)
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
