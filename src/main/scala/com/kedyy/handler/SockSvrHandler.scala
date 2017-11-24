package com.kedyy.handler

import com.kedyy.Logger
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.v5._

class SockSvrHandler(client: Bootstrap) extends ChannelInboundHandlerAdapter with Logger {
  var remote: Option[ChannelFuture] = None

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    logger info "client close"
    if (remote.isEmpty) logger info "remote null"

    remote.get.channel().close()
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
                  logger info s"connect remote ${v5.dstAddr()}:${v5.dstPort()} success"
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
          case Socks5CommandType.UDP_ASSOCIATE =>
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, v5.dstAddrType()))

          case Socks5CommandType.BIND =>
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, v5.dstAddrType()))
        }

      case buff: ByteBuf =>
        logger info "forward client's message to remote_server"
        if (remote.isEmpty) logger info "remote null"

        remote.get.channel().writeAndFlush(buff)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger error s"${ctx.channel().remoteAddress()} error::" + cause.getMessage
    cause.printStackTrace()
  }
}

