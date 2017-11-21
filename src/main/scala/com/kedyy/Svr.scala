package com.kedyy

import com.kedyy.handler.SockCipher.CipherHandler
import io.netty.bootstrap.{Bootstrap, ServerBootstrap}
import io.netty.buffer.ByteBuf
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.handler.codec.socksx.v5._

class Svr extends Logger {

  val group = new NioEventLoopGroup()
  val server = new ServerBootstrap()
  val client = new Bootstrap()

  private[this] val serverLogic = new ChannelInitializer[SocketChannel] {

    override def initChannel(ch: SocketChannel): Unit = {

      ch.pipeline().addLast(new CipherHandler(true, "aes-128-cfb", "123ss"))
      ch.pipeline().addLast(Socks5ServerEncoder.DEFAULT)
      ch.pipeline().addLast(new Socks5InitialRequestDecoder)
      ch.pipeline().addLast(new Socks5CommandRequestDecoder)
      ch.pipeline().addLast(new SockSvrHandler)
    }
  }


  class SockSvrHandler extends ChannelInboundHandlerAdapter {
    var remote: Option[ChannelFuture] = None

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
      logger info "client close"
      remote.foreach(c => c.channel().close())
    }

    override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
      msg match {

        case auth: DefaultSocks5InitialRequest =>
          // Socks5 接入认证
          ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
          ctx.channel().pipeline().remove(classOf[Socks5InitialRequestDecoder])
          logger info s"$auth"


        case v5: DefaultSocks5CommandRequest =>
          // Sock5 指令（要求 代理、绑定、或者其他）
          v5.`type`() match {
            case Socks5CommandType.CONNECT =>
              remote = Some(client
                .handler(new ChannelInitializer[SocketChannel] {
                  override def initChannel(ch: SocketChannel): Unit = {
                  }
                })
                .connect(v5.dstAddr(), v5.dstPort()).addListener(new ChannelFutureListener {
                override def operationComplete(future: ChannelFuture): Unit = {
                  if (future.isSuccess) {
                    logger info s"connect remote ${v5.toString} success"
                    ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, v5.dstAddrType()))
                    future.channel().pipeline().addLast(new ChannelInboundHandlerAdapter {
                      override def channelRead(cc: ChannelHandlerContext, msg: scala.Any): Unit = {
                        logger info "forward remote_server's answer to client"
                        ctx.writeAndFlush(msg)
                      }

                      override def channelInactive(c: ChannelHandlerContext): Unit = {
                        logger info s"remote $v5 close"
                        ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, v5.dstAddrType()))
                      }
                    })
                  } else {
                    ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, v5.dstAddrType()))
                  }
                }
              }))
          }

        case buff: ByteBuf =>
          logger info "forward client's message to remote_server"
          remote.foreach(future => future.channel().writeAndFlush(buff))
      }
    }
  }

  def loop(): ChannelFuture = {
    client.group(group).channel(classOf[NioSocketChannel])

    server.group(group).channel(classOf[NioServerSocketChannel])
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
      .childOption[java.lang.Boolean](ChannelOption.SO_REUSEADDR, true)
      .childHandler(serverLogic)
      .bind(8080)
  }
}

object svr extends App {
  new Svr().loop()
}
