package remotely
package test

import DescribeTestNewerProtocol._

@GenServer(remotely.test.DescribeTestOlderProtocol.definition) abstract class DescribeTestOlderServer
@GenServer(remotely.test.DescribeTestNewerProtocol.definition) abstract class DescribeTestNewerServer
@GenClient(remotely.test.DescribeTestOlderProtocol.definition.signatures) object DescribeTestOlderClient
@GenClient(remotely.test.DescribeTestNewerProtocol.definition.signatures) object DescribeTestNewerClient
