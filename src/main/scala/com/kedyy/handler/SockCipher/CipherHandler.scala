package com.kedyy.handler.SockCipher

import java.security.{MessageDigest, SecureRandom}
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

import com.kedyy.Logger
import com.kedyy.utils.DataUtil
import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPromise}
import org.bouncycastle.jce.provider.BouncyCastleProvider

class CipherHandler(isServer: Boolean, cipherName: String, key: String) extends ChannelDuplexHandler with Logger with DataUtil {
  final val secure = SecureObject(isServer, cipherName, key)

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit = msg match {
    case byteBuf: ByteBuf =>
      //数据流入时 读取然后需要解密
      val array = new Array[Byte](byteBuf.readableBytes())
      byteBuf.readBytes(array)
      byteBuf.release()
      val plain = secure.decrypt(array)
      ctx.fireChannelRead(ctx.alloc().buffer(plain.length).writeBytes(plain))
  }

  override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit = msg match {
    //发送时对数据进行加密操作

    case byteBuf: ByteBuf =>
      val plain = new Array[Byte](byteBuf.readableBytes())
      byteBuf.readBytes(plain)
      byteBuf.release()
      val cipher = secure.encrypt(plain)
      val buff = ctx.alloc().buffer(cipher.length)
      buff.writeBytes(cipher)
      super.write(ctx, buff, promise)
  }
}


class SecureObject(isServer: Boolean, cipherName: String, key: String) extends Logger with DataUtil {

  private def getKeySize: Int = {
    //"AES/CFB/NoPadding"
    16
  }

  private def getIvSize: Int = {
    //"AES/CFB/NoPadding"
    16
  }

  def randBytes(len: Int): Array[Byte] = {
    val p = new Array[Byte](len)
    new SecureRandom().nextBytes(p)
    p
  }

  def kdf(): (Array[Byte], Array[Byte]) = {
    val password = key.getBytes()
    val m: scala.collection.mutable.Queue[Array[Byte]] = scala.collection.mutable.Queue()
    val md5 = MessageDigest.getInstance("MD5")
    var data = password
    var c = 0
    while (m.flatten.size < getKeySize + getIvSize) {
      if (c > 0)
        data = m(c - 1) ++ password
      md5.update(data)
      m.enqueue(md5.digest())
      c += 1
    }
    val bkey = m.flatten.take(getKeySize).toArray
    val biv = m.flatten.slice(getKeySize, getKeySize + getIvSize).toArray
    (bkey, biv)
  }

  var ivSend: Boolean = false
  var ivRecv: Boolean = false
  private[this] val e = Cipher.getInstance("AES/CFB/NoPadding", new BouncyCastleProvider())
  private[this] val d = Cipher.getInstance("AES/CFB/NoPadding", new BouncyCastleProvider())
  val (clearKey, _) = kdf()
  logger info "Key::" + ArrayByte2HexString(clearKey)

  def encrypt(bytes: Array[Byte]): Array[Byte] = {
    logger info "before encrypt:" + ArrayByte2HexString(bytes)
    val c = if (!ivSend) {
      ivSend = true
      val randIv = randBytes(getIvSize)
      e.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clearKey, "AES"), new IvParameterSpec(randIv))
      randIv ++ e.update(bytes)
    }
    else e.update(bytes)
    logger info "after encrypt:" + ArrayByte2HexString(c)
    c
  }

  def decrypt(bytes: Array[Byte]): Array[Byte] = {
    logger info "int:" + ArrayByte2HexString(bytes)
    val c =
      if (!ivRecv) {
        val civ = bytes.take(getIvSize)
        ivRecv = true
        logger info "IV::" + ArrayByte2HexString(civ)
        d.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clearKey, "AES"), new IvParameterSpec(civ))
        d.update(bytes.drop(getIvSize))
      }
      else d.update(bytes)
    logger info "after decrypt:" + ArrayByte2HexString(c)
    c
  }
}

object SecureObject {
  def apply(isServer: Boolean, cipherName: String, key: String): SecureObject = new SecureObject(isServer, cipherName, key)
}


object test extends App with DataUtil {
  val d = Cipher.getInstance("AES/CFB/NoPadding", new BouncyCastleProvider())
  d.init(Cipher.DECRYPT_MODE, new SecretKeySpec(HexString2Bytes("202CB962AC59075B964B07152D234B70"), "AES"),
    new IvParameterSpec(HexString2Bytes("122F7A0FDE101B720FC6C06A6FA87886")))
  println(ArrayByte2HexString(d.update(HexString2Bytes("4A3F79BBD984B8CA450F5D0BA4B6B47869"))))
}