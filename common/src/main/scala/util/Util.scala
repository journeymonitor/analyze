package com.journeymonitor.analyze.common.util

import java.text.SimpleDateFormat
import java.util.Calendar

import scala.collection.{Seq, mutable}

object Util {
  def fullDatetimeWithRfc822Tz(calendar: Calendar): String = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")
    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    sdf.format(calendar.getTime)
  }

  def yMd(calendar: Calendar): String = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd");
    sdf.format(calendar.getTime)
  }

  def getDayBuckets(datetime: java.util.Date): Seq[String] = {
    val current = Calendar.getInstance()
    current.setTime(datetime)

    val today = Calendar.getInstance()

    if (current.after(today)) {
      Seq(yMd(today))
    }

    val l = mutable.MutableList.empty[String]
    l += yMd(current)

    while (yMd(current) != yMd(today)) {
      current.add(Calendar.DATE, 1)
      l += yMd(current)
    }

    l.toSeq.reverse
  }
}
