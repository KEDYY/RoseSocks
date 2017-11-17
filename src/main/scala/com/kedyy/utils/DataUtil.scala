package com.kedyy.utils

trait DataUtil {
  def Int2HexString(num: Int): String = {
    num.toHexString.toUpperCase.reverse.padTo(8, '0').reverse
  }

  def ArrayByte2HexString(array: Array[Byte]): String = {
    val hexString = new StringBuilder(array.length * 2)
    array.foreach(byte => hexString.append("%02X" format byte))
    hexString.toString()
  }

  def HexString2Bytes(string: String): Array[Byte] = {
    var offset = 0
    val result = new Array[Byte](string.length / 2)
    while (offset < string.length) {
      result.update(offset / 2, Integer.parseInt(string.substring(offset, offset + 2), 16).toByte)
      offset += 2
    }
    result
  }
}