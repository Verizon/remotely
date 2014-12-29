package remotely
package test

import DescribeTestProtocol._

@GenServer(remotely.test.DescribeTestProtocol.definition) abstract class DescribeTestServer
@GenClient(remotely.test.DescribeTestProtocol.definition.signatures) object DescribeTestClient
