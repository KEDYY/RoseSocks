package com.kedyy

import com.kedyy.handler.SockCipher.CipherHandler
import com.kedyy.handler.SockSvrHandler
import com.kedyy.handler.shadowsocks.ShadowSocksHandler
import io.netty.bootstrap.{Bootstrap, ServerBootstrap}
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.handler.codec.socksx.v5.{Socks5CommandRequestDecoder, Socks5InitialRequestDecoder, Socks5ServerEncoder}

class Svr extends Logger {

  val group = new NioEventLoopGroup()
  val client = new Bootstrap()

  private[this] val socks5 = new ChannelInitializer[SocketChannel] {

    override def initChannel(ch: SocketChannel): Unit = {
      ch.pipeline().addLast(Socks5ServerEncoder.DEFAULT)
      ch.pipeline().addLast(new Socks5InitialRequestDecoder)
      ch.pipeline().addLast(new Socks5CommandRequestDecoder)
      ch.pipeline().addLast(new SockSvrHandler(client))
    }
  }

  private[this] val roseSocks = new ChannelInitializer[SocketChannel] {

    override def initChannel(ch: SocketChannel): Unit = {
      ch.pipeline().addLast(new CipherHandler(true, "aes-128-cfb", "123"))
      ch.pipeline().addLast(Socks5ServerEncoder.DEFAULT)
      ch.pipeline().addLast(new Socks5InitialRequestDecoder)
      ch.pipeline().addLast(new Socks5CommandRequestDecoder)
      ch.pipeline().addLast(new SockSvrHandler(client))
    }
  }

  private[this] val ss = new ChannelInitializer[SocketChannel] {

    override def initChannel(ch: SocketChannel): Unit = {
      ch.pipeline().addLast(new CipherHandler(true, "aes-128-cfb", "123"))
      ch.pipeline().addLast(new ShadowSocksHandler(client))
    }
  }


  def loop(): ChannelFuture = {
    client.group(group).channel(classOf[NioSocketChannel])

    new ServerBootstrap().group(group).channel(classOf[NioServerSocketChannel])
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
      .childOption[java.lang.Boolean](ChannelOption.SO_REUSEADDR, true)
      .childOption[java.lang.Integer](ChannelOption.SO_BACKLOG, 4096)
      .childOption[java.lang.Integer](ChannelOption.SO_LINGER, 0)
      .childHandler(socks5)
      .bind(1080)

    new ServerBootstrap().group(group).channel(classOf[NioServerSocketChannel])
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, false)
      .childOption[java.lang.Boolean](ChannelOption.SO_REUSEADDR, true)
      .childHandler(roseSocks)
      .bind(8443)

    new ServerBootstrap().group(group).channel(classOf[NioServerSocketChannel])
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, false)
      .childOption[java.lang.Boolean](ChannelOption.SO_REUSEADDR, true)
      .childHandler(ss)
      .bind(8444)
  }
}

object svr extends App {
  new Svr().loop()
}
