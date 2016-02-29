package com.journeymonitor.analyze.common.util

import java.text.SimpleDateFormat
import java.util.Calendar

import scala.collection.{mutable, Seq}

object Util {
  def getDayBuckets(datetime: java.util.Date): Seq[String] = {

    def asString(calendar: Calendar) = {
      val sdf = new SimpleDateFormat("yyyy-MM-dd");
      sdf.format(calendar.getTime)
    }

    val current = Calendar.getInstance()
    current.setTime(datetime)

    val today = Calendar.getInstance()

    if (current.after(today)) {
      Seq(asString(today))
    }

    val l = mutable.MutableList.empty[String]
    l += asString(current)

    while (asString(current) != asString(today)) {
      current.add(Calendar.DATE, 1)
      l += asString(current)
    }

    l.toSeq.reverse
  }
}
