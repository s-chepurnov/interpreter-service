package test.sigmastate.utxo
import scapi.sigma.DLogProtocol.DLogProverInput
import scapi.sigma.{DiffieHellmanTupleProverInput, SigmaProtocolPrivateInput}
import scorex.utils.Random
import sigmastate.Values._
import sigmastate.SType
import sigmastate.interpreter.ProverInterpreter
import sigmastate.utxo.{CostTable, ErgoInterpreter}

class ErgoProvingInterpreter(override val maxCost: Int = CostTable.ScriptLimit)
  extends ErgoInterpreter(maxCost) with ProverInterpreter {

  import sigmastate.interpreter.GroupSettings.soundness

  override lazy val secrets: Seq[SigmaProtocolPrivateInput[_, _]] = {
    (1 to 4).map(_ => DLogProverInput.random()) ++
      (1 to 4).map(_ => DiffieHellmanTupleProverInput.random())
  }

  lazy val dlogSecrets: Seq[DLogProverInput] =
    secrets.filter(_.isInstanceOf[DLogProverInput]).asInstanceOf[Seq[DLogProverInput]]

  lazy val dhSecrets: Seq[DiffieHellmanTupleProverInput] =
    secrets.filter(_.isInstanceOf[DiffieHellmanTupleProverInput]).asInstanceOf[Seq[DiffieHellmanTupleProverInput]]

  override lazy val contextExtenders: Map[Byte, EvaluatedValue[_ <: SType]] = (1 to 10).map { i =>
    val ba = Random.randomBytes(75)
    i.toByte -> ByteArrayConstant(ba)
  }.toMap

  def withContextExtender(tag: Byte, value: EvaluatedValue[_ <: SType]): ErgoProvingInterpreter = {
    val s = secrets
    val ce = contextExtenders

    new ErgoProvingInterpreter(maxCost) {
      override lazy val secrets = s
      override lazy val contextExtenders: Map[Byte, EvaluatedValue[_ <: SType]] = ce + (tag -> value)
    }
  }

  def withSecrets(additionalSecrets: Seq[DLogProverInput]): ErgoProvingInterpreter = {
    val ce = contextExtenders
    val s = secrets ++ additionalSecrets

    new ErgoProvingInterpreter(maxCost) {
      override lazy val secrets = s
      override lazy val contextExtenders: Map[Byte, EvaluatedValue[_ <: SType]] = ce
    }
  }
}
