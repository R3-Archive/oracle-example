package net.corda.examples.oracle

import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.node.services.ServiceInfo
import net.corda.examples.oracle.flow.CreatePrime
import net.corda.examples.oracle.service.Primes
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test

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
        val serviceInfo = ServiceInfo(Primes.type)
        oracle = mockNet.createNode(nodes.mapNode.info.address, advertisedServices = serviceInfo)
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
        val result = flow.resultFuture.getOrThrow()
        println(result.tx.outputs.single().data)
    }

}
