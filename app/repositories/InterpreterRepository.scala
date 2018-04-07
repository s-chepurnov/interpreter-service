package repositories

import javax.inject.Singleton
import models.EnvironmentDTO
import play.api.Logger
import scorex.crypto.hash.Blake2b256
import sigmastate.Values.ByteArrayConstant
import sigmastate.utxo.ErgoInterpreter
import test.sigmastate.utxo.ErgoProvingInterpreter

@Singleton
class InterpreterRepository() {
  private val logger = Logger(this.getClass)

  val proverA = new ErgoProvingInterpreter
  val proverB = new ErgoProvingInterpreter
  val pubkeyA = proverA.dlogSecrets.head.publicImage
  val pubkeyB = proverB.dlogSecrets.head.publicImage
  val verifier = new ErgoInterpreter

  val x = proverA.contextExtenders(1).value.asInstanceOf[Array[Byte]]
  val hx = ByteArrayConstant(Blake2b256(x))

  val height1 = 100000
  val height2 = 50000

  val deadlineA = 1000
  val deadlineB = 500
  var env = Map(
    "height1" -> height1,
    "height2" -> height2,
    "deadlineA" -> deadlineA,
    "deadlineB" -> deadlineB,
    "pubkeyA" -> pubkeyA,
    "pubkeyB" -> pubkeyB,
    "hx" -> hx)

//  def list: Map[String, Any] = {
//    val r3 = env.map(e => EnvironmentDTO(e._1,e._2))
//  }

  def list = {
    /*Seq(
      EnvironmentDTO("height1", height1),
      EnvironmentDTO("height2", height2),
      EnvironmentDTO("deadlineA", deadlineA),
      EnvironmentDTO("deadlineB", deadlineB),
      EnvironmentDTO("pubkeyA", pubkeyA),
      EnvironmentDTO("pubkeyB", pubkeyB),
      EnvironmentDTO("hx", hx)
    )*/
    env.map(i => EnvironmentDTO(i._1, i._2))
  }

  def getEnv = {
    env
  }

  def save(key: String, value: Any): Unit = {
    env += (key -> value)
  }

  def clear() : Unit = {
    env = Map()
  }

}
