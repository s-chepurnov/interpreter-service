package repositories

import javax.inject.Singleton
import play.api.Logger
import scorex.crypto.hash.Blake2b256
import sigmastate.Values.ByteArrayConstant
import sigmastate.utxo.ErgoInterpreter
import test.sigmastate.utxo.ErgoProvingInterpreter

@Singleton
class InterpreterRepository() {
  private val logger = Logger(this.getClass)

  var env = getAtomic()

  //TODO : delete it
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
  }

  def put(name: String): Unit = {
    name match {
      case "atomic" => env = getAtomic
      case "ring" => env = getRing
      case "demurrage" => env = getDemurrage
    }
  }

  def clear() : Unit = {
    env = Map()
  }

  def getAtomic() = {
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

    Map[String, Any](
      "height1" -> height1,
      "height2" -> height2,
      "deadlineA" -> deadlineA,
      "deadlineB" -> deadlineB,
      "pubkeyA" -> pubkeyA,
      "pubkeyB" -> pubkeyB,
      "hx" -> hx)
  }

  def getRing() = {
    val proverA = new ErgoProvingInterpreter
    val proverB = new ErgoProvingInterpreter
    val proverC = new ErgoProvingInterpreter
    val verifier = new ErgoInterpreter

    val pubkeyA = proverA.dlogSecrets.head.publicImage
    val pubkeyB = proverB.dlogSecrets.head.publicImage

    val env = Map("pubkeyA" -> pubkeyA, "pubkeyB" -> pubkeyB)
    env
    //val compiledProp = compile(env, """pubkeyA || pubkeyB""")
  }

  def getDemurrage() = {
    val demurragePeriod = 100
    val demurrageCost = 2

    //a blockchain node veryfing a block containing a spending transaction
    val verifier = new ErgoInterpreter

    //backer's prover with his private key
    val userProver = new ErgoProvingInterpreter

    val regScript = userProver.dlogSecrets.head.publicImage

    val env = Map(
      "demurragePeriod" -> demurragePeriod,
      "demurrageCost" -> demurrageCost,
      "regScript" -> regScript
    )
    env
  }
}
