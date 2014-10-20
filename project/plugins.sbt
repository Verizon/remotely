resolvers += "im.nexus" at "http://nexus.svc.oncue.com/nexus/content/groups/intel_media_maven/"

addSbtPlugin("oncue.build" %% "sbt-oncue" % "6.4.+")

addSbtPlugin("org.brianmckenna" % "sbt-wartremover" % "0.11")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
