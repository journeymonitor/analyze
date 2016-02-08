package com.journeymonitor.analyze.common.repositories

import com.journeymonitor.analyze.common.models.Model

import scala.util.Try

abstract trait Repository[M <: Model, I] {
  def getNById(id: I, n: Int): Try[List[M]]
}
