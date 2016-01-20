package repositories

import models.Model

abstract trait Repository[M <: Model, I] {
  def getOneById(id: I): M
  def getNById(id: I, n: Int): List[M]
}
