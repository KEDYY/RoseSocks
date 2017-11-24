package com.kedyy.handler.shadowsocks

import com.kedyy.Logger
import com.kedyy.utils.DataUtil
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.v5._


class ShadowSocksHandler(client: Bootstrap) extends ChannelInboundHandlerAdapter with Logger with DataUtil {
  private val queue = scala.collection.mutable.Queue[ByteBuf]()
  private var remote: ChannelFuture = _

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    logger info s"client ${ctx.channel().remoteAddress()} close"
    if (null != remote) remote.channel().close()
  }


  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
    msg match {
      case in: ByteBuf =>
        if (null == remote) {
          val dstAddr = Socks5AddressDecoder.DEFAULT.decodeAddress(Socks5AddressType.valueOf(in.readByte), in)
          val dstPort = in.readUnsignedShort

          remote = client.handler(new ChannelInitializer[SocketChannel] {
            override def initChannel(ch: SocketChannel): Unit = {
            }
          }).connect(dstAddr, dstPort).addListener(new ChannelFutureListener {
            override def operationComplete(future: ChannelFuture): Unit = {
              if (future.isSuccess) {
                logger info s"remote $dstAddr:$dstPort connect success"
              } else {
                logger info s"remote $dstAddr:$dstPort connect failed::" + future.cause().getMessage
                ctx.close()
              }
            }
          })

          remote.channel().pipeline().addLast(new ChannelDuplexHandler {
            override def channelRead(cc: ChannelHandlerContext, msg: scala.Any): Unit = {
              logger info "forward remote_server's answer to client"
              ctx.writeAndFlush(msg)
            }

            override def channelInactive(c: ChannelHandlerContext): Unit = {
              logger info s"remote $dstAddr:$dstPort close"
              ctx.close()
            }

            override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit = {
              msg match {
                case bf: ByteBuf =>
                  val d = new Array[Byte](bf.readableBytes())
                  bf.copy().readBytes(d).release()
                  super.write(ctx, bf, promise)
              }
            }
          })
        }

        logger debug "queue client request message"
        queue.enqueue(in)

        if (remote.channel().isActive) {
          queue.dequeueAll(bf => {
            logger info "forward client's message to remote_server"
            remote.channel().writeAndFlush(bf)
            true
          })
        } else {
          remote.addListener(new ChannelFutureListener {
            override def operationComplete(future: ChannelFuture): Unit = {
              if (future.isSuccess) {
                queue.dequeueAll(bf => {
                  logger info "call::forward client's message to remote_server"
                  remote.channel().writeAndFlush(bf)
                  true
                })
              }
            }
          })
        }
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    //不需要急于关闭连接 等待客户端主动关闭时触发即可
    logger error s"${ctx.channel().remoteAddress()} error::" + cause.getMessage
    cause.printStackTrace()
  }
}
