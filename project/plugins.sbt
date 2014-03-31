resolvers += "im.nexus" at "http://nexus.svc.m.infra-host.com/nexus/content/groups/intel_media_maven/"

addSbtPlugin("oncue.build" %% "sbt-oncue" % "6.1.10")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
