import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import common.promptSettings

name := "remotely-docs"

site.settings

tutSettings

promptSettings

site.addMappingsToSiteDir(tut, "")

ghpages.settings

ghpagesNoJekyll := false

includeFilter in makeSite := "*.yml" | "*.md" | "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf"

git.remoteRepo := "git@github.com:oncue/remotely.git"

publish := {}
