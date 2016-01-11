package repositories

import models.Model

abstract trait Repository[M <: Model, I] {
  def getOneById(id: I): M
}
