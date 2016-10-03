
resolvers += Resolver.url(
  "tpolecat-sbt-plugin-releases",
    url("http://dl.bintray.com/content/tpolecat/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

addSbtPlugin("io.verizon.build" % "sbt-rig" % "1.1.20")

// docs
addSbtPlugin("com.typesafe.sbt"  % "sbt-site"        % "0.8.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-ghpages"     % "0.5.3")
addSbtPlugin("org.tpolecat"      % "tut-plugin"      % "0.3.2")
addSbtPlugin("com.eed3si9n"      % "sbt-unidoc"      % "0.3.2")
addSbtPlugin("org.brianmckenna"  % "sbt-wartremover" % "0.11")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"    % "0.11.2")
