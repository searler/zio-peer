


scalaVersion := "2.13.1"


name := "zio-peer"
organization := "searler"
version := "0.3"


val zio_version ="1.0.9"

libraryDependencies += "dev.zio" %% "zio" % zio_version
libraryDependencies += "dev.zio" %% "zio-streams" % zio_version
libraryDependencies += "dev.zio" %% "zio-test"          % zio_version % "test"
libraryDependencies +=  "dev.zio" %% "zio-test-sbt"      % zio_version % "test"

libraryDependencies += "searler" %% "zio-tcp" % "0.2"

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

githubOwner := "searler"
githubRepository := "zio-peer"

githubTokenSource := TokenSource.GitConfig("github.token")

resolvers += Resolver.githubPackages("searler", "zio-tcp")


