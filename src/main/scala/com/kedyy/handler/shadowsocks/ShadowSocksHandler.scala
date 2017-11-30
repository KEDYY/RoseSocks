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
  private val domain = "[a-z0-9][a-z0-9\\.\\-]{1,65}[a-z]"
  private val ipv4 = "[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}"
  private val ipv6 = "^([\\da-fA-F]{1,4}:){6}((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$|^::([\\da-fA-F]{1,4}:){0,4}((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$|^([\\da-fA-F]{1,4}:):([\\da-fA-F]{1,4}:){0,3}((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$|^([\\da-fA-F]{1,4}:){2}:([\\da-fA-F]{1,4}:){0,2}((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$|^([\\da-fA-F]{1,4}:){3}:([\\da-fA-F]{1,4}:){0,1}((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$|^([\\da-fA-F]{1,4}:){4}:((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$|^([\\da-fA-F]{1,4}:){7}[\\da-fA-F]{1,4}$|^:((:[\\da-fA-F]{1,4}){1,6}|:)$|^[\\da-fA-F]{1,4}:((:[\\da-fA-F]{1,4}){1,5}|:)$|^([\\da-fA-F]{1,4}:){2}((:[\\da-fA-F]{1,4}){1,4}|:)$|^([\\da-fA-F]{1,4}:){3}((:[\\da-fA-F]{1,4}){1,3}|:)$|^([\\da-fA-F]{1,4}:){4}((:[\\da-fA-F]{1,4}){1,2}|:)$|^([\\da-fA-F]{1,4}:){5}:([\\da-fA-F]{1,4})?$|^([\\da-fA-F]{1,4}:){6}:$"

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    logger info s"client ${ctx.channel().remoteAddress()} close"
    if (null != remote) remote.channel().close()
  }


  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = {
    msg match {
      case in: ByteBuf =>
        if (null == remote) {
          val dstType = Socks5AddressType.valueOf(in.readByte)
          val dstAddr = Socks5AddressDecoder.DEFAULT.decodeAddress(dstType, in)
          val dstPort = in.readUnsignedShort
          dstType match {
            case Socks5AddressType.DOMAIN =>
              if (!dstAddr.matches(domain)) {
                in.discardReadBytes()
                return // DROP
              }
            case Socks5AddressType.IPv4 =>
              if (!dstAddr.matches(ipv4)) {
                in.discardReadBytes()
                return // DROP
              }
            case Socks5AddressType.IPv6 =>
              if (!dstAddr.matches(ipv6)) {
                in.discardReadBytes()
                return // DROP
              }

          }
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
                  ctx.write(bf, promise)
              }
            }
          })
        }

        logger debug "queue client request message"
        queue.enqueue(in.copy())
        in.release()

        if (remote.channel().isActive) {
          queue.dequeueAll(bf => {
            logger info "forward client's message to remote_server"
            remote.channel().write(bf)
            true
          })
          remote.channel().flush()
        } else {
          remote.addListener(new ChannelFutureListener {
            override def operationComplete(future: ChannelFuture): Unit = {
              if (future.isSuccess) {
                queue.dequeueAll(bf => {
                  logger info "call::forward client's message to remote_server"
                  remote.channel().write(bf)
                  true
                })
                remote.channel().flush()
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
