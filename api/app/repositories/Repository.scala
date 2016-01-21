package repositories

import models.Model

abstract trait Repository[M <: Model, I] {
  def getNById(id: I, n: Int): List[M]
}
