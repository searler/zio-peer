


scalaVersion := "2.13.1"


name := "zio-peer"
organization := "searler"
version := "0.1"


val zio_version ="1.0.9"

libraryDependencies += "dev.zio" %% "zio" % zio_version
libraryDependencies += "dev.zio" %% "zio-streams" % zio_version
libraryDependencies += "dev.zio" %% "zio-test"          % zio_version % "test"
libraryDependencies +=  "dev.zio" %% "zio-test-sbt"      % zio_version % "test"
libraryDependencies += "dev.zio" %% "zio-json" % "0.1.5"

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

resolvers += "LocalFileSystem" at "file:///${user.home}/.ivy2/local/"

libraryDependencies += "searler" %% "zio-tcp" % "0.1"
