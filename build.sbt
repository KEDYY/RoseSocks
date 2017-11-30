

name := "SocksWithCipher"

version := "0.2"

description := "Netty Socks5 服务器（输入输出流进行加密）"
scalaVersion := "2.11.8"

mainClass in (Compile, run) := Some("com.kedyy.svr")

libraryDependencies ++= Seq(
  "io.netty" % "netty-all" % "4.1.15.Final",
  "log4j" % "log4j" % "1.2.17",
  "commons-logging" % "commons-logging" % "1.2",
  "org.bouncycastle" % "bcprov-jdk16" % "1.46"
)
enablePlugins(AssemblyPlugin)
//unmanagedJars ++= Seq()