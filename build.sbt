lazy val Scalatest = "org.scalatest" %% "scalatest" % "3.0.8"

lazy val commonSettings = Def.settings(
  scalaVersion := "2.12.8"
)

lazy val ideaSettings = Def.settings(
  ThisBuild / ideaPluginName := "mu-idea",
  ThisBuild / ideaEdition := IdeaEdition.Community,
  ThisBuild / ideaBuild := "191.6183.87",
  ideaInternalPlugins := Seq(),
  ideaExternalPlugins += IdeaPlugin.Id("Scala",
                                       "org.intellij.scala",
                                       Some("eap"))
)

lazy val packagingSettings = Def.settings(
  packageMethod := PackagingMethod.Standalone()
)

lazy val muIdeaPlugin: Project = project
  .in(file("."))
  .settings(commonSettings)
  .settings(ideaSettings)
  .settings(packagingSettings)
  .settings(
    name := "mu-idea",
    libraryDependencies ++= Seq(Scalatest % Test)
  )
  .enablePlugins(SbtIdeaPlugin)

lazy val ideaRunner: Project =
  createRunnerProject(muIdeaPlugin, "idea-runner")
  