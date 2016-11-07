name := "stream_utils"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.12",
  "com.typesafe.akka" %% "akka-persistence" % "2.4.12",
  "com.github.nscala-time" %% "nscala-time" % "2.14.0",
  "org.slf4j" % "slf4j-jdk14" % "1.7.21",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.13",
  "org.iq80.leveldb"            % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
)


