
scalacOptions in Compile := (scalacOptions in Compile).value.filterNot(f => f == "-Xlint" || f == "-Xfatal-warnings")

enablePlugins(DisablePublishingPlugin)
