package controllers

import javax.inject.Inject
import models.EnvironmentDTO
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.mvc._
import repositories.InterpreterRepository
import scapi.sigma.DLogProtocol.ProveDlog
import scorex.crypto.hash.Blake2b256
import sigmastate.Values._
import sigmastate._
import sigmastate.utxo.{ErgoContext, ErgoInterpreter}
import test.sigmastate.utxo.BoxHelpers.{fakeMessage, fakeSelf}
import test.sigmastate.utxo.{ErgoInterpreterSpecification, ErgoProvingInterpreter}

class InterpreterController @Inject()(cc: ControllerComponents, interpreterRepository: InterpreterRepository)
                                                                                      extends AbstractController(cc) {

  private val logger = Logger(getClass)

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def addContext(key: String, value: Any) = Action {implicit request =>
    //TODO
    interpreterRepository.save(key, value)
    Ok(Json.obj("result"->"ok"))
  }

  def getContext = Action {implicit request =>

    implicit val environmentDTOWrites = new Writes[EnvironmentDTO] {
      def writes(env: EnvironmentDTO) = Json.obj(
        env.value match {
          case ProveDlog => env.key.toString -> env.value.asInstanceOf[ProveDlog].bytes
          case ByteArrayConstant => env.key.toString -> env.value.asInstanceOf[ByteArrayConstant].value
          case _ => {println("always"); env.key.toString -> env.value.toString}
        }
      )
    }

    Ok(Json.toJson(interpreterRepository.list))
  }

  def asBoolValue(v: Value[SType]): Value[SBoolean.type] = v.asInstanceOf[Value[SBoolean.type]]

  def test = Action { implicit request =>

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

    val env = Map(
      "height1" -> height1,
      "height2" -> height2,
      "deadlineA" -> deadlineA,
      "deadlineB" -> deadlineB,
      "pubkeyA" -> pubkeyA,
      "pubkeyB" -> pubkeyB,
      "hx" -> hx)

    //env += interpreterRepository.list

    val prop1 : Value[SBoolean.type] = asBoolValue(new ErgoInterpreterSpecification().compile(env,
      """{
        |  anyOf(Array(
        |    HEIGHT > height1 + deadlineA && pubkeyA,
        |    pubkeyB && blake2b256(taggedByteArray(1)) == hx
        |  ))
        |}""".stripMargin))

    //chain1 script
//    val prop1Tree = OR(
//      AND(GT(Height, Plus(IntConstant(height1), IntConstant(deadlineA))), pubkeyA),
//      AND(pubkeyB, EQ(CalcBlake2b256(TaggedByteArray(1)), hx))
//    )

    val prop2 = asBoolValue(new ErgoInterpreterSpecification().compile(env,
      """{
        |  anyOf(Array(
        |    HEIGHT > height2 + deadlineB && pubkeyB,
        |    pubkeyA && blake2b256(taggedByteArray(1)) == hx
        |  ))
        |}
      """.stripMargin))

//    val prop2Tree = OR(
//      AND(GT(Height, Plus(IntConstant(height2), IntConstant(deadlineB))), pubkeyB),
//      AND(pubkeyA, EQ(CalcBlake2b256(TaggedByteArray(1)), hx))
//    )

    //Successful run below:
    //A spends coins of B in chain2
    val ctx1 = ErgoContext(
      currentHeight = height2 + 1,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      boxesToSpend = IndexedSeq(),
      spendingTransaction = null,
      self = fakeSelf)
    val pr = proverA.prove(prop2, ctx1, fakeMessage).get
    //verifier.verify(prop2, ctx1, pr, fakeMessage).get shouldBe true

    //B extracts preimage x of hx
    val t = pr.extension.values(1)
    val proverB2 = proverB.withContextExtender(1, t.asInstanceOf[ByteArrayConstant])

    //B spends coins of A in chain1 with knowledge of x
    val ctx2 = ErgoContext(
      currentHeight = height1 + 1,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      boxesToSpend = IndexedSeq(),
      spendingTransaction = null,
      self = fakeSelf)
    val pr2 = proverB2.prove(prop1, ctx2, fakeMessage).get
    //verifier.verify(prop1, ctx2, pr2, fakeMessage).get shouldBe true

    if (verifier.verify(prop2, ctx1, pr, fakeMessage).get && verifier.verify(prop1, ctx2, pr2, fakeMessage).get) {
      Ok(Json.obj("result"->"success"))
    } else {
      Ok(Json.obj("result"->"failure"))
    }
  }

  def compileScript(script: String) =  Action {implicit request =>
    //val map = interpreterRepository.list
    println(script)
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

    val env = Map(
      "height1" -> height1,
      "height2" -> height2,
      "deadlineA" -> deadlineA,
      "deadlineB" -> deadlineB,
      "pubkeyA" -> pubkeyA,
      "pubkeyB" -> pubkeyB,
      "hx" -> hx)

    var msg = ""
    try {
      asBoolValue(new ErgoInterpreterSpecification().compile(env, script))
    } catch {
      case e: Exception => {msg = e.getMessage}
    }

    if(msg.isEmpty)
      Ok(Json.obj("result"->"success"))
    else
      Ok(Json.obj("result"->"failure","msg"->msg))
  }
}
