import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._

site.settings

tutSettings

site.addMappingsToSiteDir(tut, "")

ghpages.settings

ghpagesNoJekyll := false

includeFilter in makeSite := "*.yml" | "*.md" | "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf"

git.remoteRepo := "git@github.com:stew/remotely.git"
