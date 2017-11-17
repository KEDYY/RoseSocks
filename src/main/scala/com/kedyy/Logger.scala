package com.kedyy


import org.apache.log4j


trait Logger {
  val logger: log4j.Logger = log4j.Logger.getLogger(this.getClass)
}
