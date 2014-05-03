name := "brain"

version := "0.3.1"

organization := "Israel Freitas"

scalaVersion := "2.10.2"

EclipseKeys.withSource := true

resolvers ++= Seq(
                  "snapshots"            at "http://oss.sonatype.org/content/repositories/snapshots",
                  "staging"              at "http://oss.sonatype.org/content/repositories/staging"  ,
                  "releases"             at "http://oss.sonatype.org/content/repositories/releases" ,
                  "Ansvia Releases Repo" at "http://scala.repo.ansvia.com/releases/"
                 )
                 
seq(webSettings :_*)

mainClass := Some("RunWebApp")

//unmanagedResourceDirectories in Test <+= (baseDirectory) { _ / "src/main/webapp" }

scalacOptions ++= Seq("-deprecation", "-unchecked")

libraryDependencies ++= {
  val liftVersion = "2.6-M2"
  Seq(
    "org.eclipse.jetty"        % "jetty-webapp"   % "8.1.12.v20130726"    % "compile,container,test",
    "org.eclipse.jetty.orbit"  % "javax.servlet"  % "3.0.0.v201112011016" % "compile,container,test" artifacts Artifact("javax.servlet", "jar", "jar"),
    "org.scalatest"            % "scalatest_2.10"              % "2.0.M8"              % "test",
    "org.mockito"              % "mockito-all"                 % "1.9.5"               % "test",
    "org.seleniumhq.selenium"  % "selenium-java"               % "2.35.0"              % "test"
  )
}
