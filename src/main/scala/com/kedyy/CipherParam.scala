package com.kedyy

import java.security.InvalidParameterException

import scala.collection.mutable


case class CipherParam(cipherName: String, keySize: Int, ivSize: Int)

object CipherParam {

  @throws[InvalidParameterException]
  def getParam(name: String): CipherParam = {
    param.getOrElse(name.toLowerCase, throw new InvalidParameterException())
  }

  private val param: mutable.HashMap[String, CipherParam] = mutable.HashMap()
  param ++= mutable.HashMap(
    "aes-128-ecb" -> new CipherParam("AES/ECB/NoPadding", 16, 16),
    "aes-128-CBC" -> new CipherParam("AES/CBC/NoPadding", 16, 16),
    "aes-128-cfb" -> new CipherParam("AES/CFB8/NoPadding", 16, 16),
    "aes-128-ofb" -> new CipherParam("AES/OFB/NoPadding", 16, 16),
    "aes-128-GCM" -> new CipherParam("AES/GCM/NoPadding", 16, 16),

    "aes-192-ecb" -> new CipherParam("AES/ECB/NoPadding", 24, 16),
    "aes-192-cbc" -> new CipherParam("AES/CBC/NoPadding", 24, 16),
    "aes-192-cfb" -> new CipherParam("AES/CFB8/NoPadding", 24, 16),
    "aes-192-ofb" -> new CipherParam("AES/OFB/NoPadding", 24, 16),
    "aes-192-gcm" -> new CipherParam("AES/GCM/NoPadding", 24, 16),

    "aes-256-ecb" -> new CipherParam("AES/ECB/NoPadding", 32, 16),
    "aes-256-cbc" -> new CipherParam("AES/CBC/NoPadding", 32, 16),
    "aes-256-cfb" -> new CipherParam("AES/CFB8/NoPadding", 32, 16),
    "aes-256-ofb" -> new CipherParam("AES/OFB/NoPadding", 32, 16),
    "aes-256-gcm" -> new CipherParam("AES/GCM/NoPadding", 32, 16)
  )

}
