package repositories

import javax.inject.Singleton
import play.api.Logger


@Singleton
class InterpreterRepository() {
  private val logger = Logger(this.getClass)

  var env = Map[String, Any](
    "height1" -> 100000,
    "height2" -> 50000,
  )

  def list: Map[String, Any] = {
    env
  }

  def save(key: String, value: Int): Unit = {
    env += (key -> value)
  }

  def remove(key: String, value: Int) : Unit = {
    //env -= (key -> value)
  }
}
