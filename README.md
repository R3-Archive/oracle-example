![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Corda Oracle Example

Currently based upon M12-SNAPSHOT (commit: 851cccbf7e2d4c3a219ce4633f76f305f1318271).

This is an Oracle service and client implementation that facilitates the querying of nth prime numbers and validation of 
nth prime numbers.

Whilst the functionality is completely superfluous (as primes can be verified deterministically via the contract code), 
it is a useful example of how to structure an Oracle service that provides querying and signing abilities.

Do read the inline comments which discuss the rationale behind the design of the Oracle service.

This repo is split into three CorDapps:

1. A base CorDapp which holds the state and contract definition, as well as some utility flow definitions which need to
   be shared by, both the Oracle service and the client
2. A client which implements a flow to query the Oracle and generate a prime number state
3. A service which implements the primes Oracle

TODO:

* Make a Java version
* Add a tutorial page on the docsite explaining how to build an oracle

