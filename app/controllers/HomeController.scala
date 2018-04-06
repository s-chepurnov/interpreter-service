package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import repositories.InterpreterRepository
import scorex.crypto.hash.Blake2b256
import sigmastate._
import sigmastate.Values._
import sigmastate.utxo.{ErgoContext, ErgoInterpreter, Height}
import test.sigmastate.utxo.{ErgoInterpreterSpecification, ErgoProvingInterpreter}
import test.sigmastate.utxo.BoxHelpers.{fakeMessage, fakeSelf}
import test.sigmastate._
import test.sigmastate.utxo.{BoxHelpers, _}

/**
  * A very small controller that renders a home page.
  */
class HomeController @Inject()(cc: ControllerComponents, interpreterRepository: InterpreterRepository) extends AbstractController(cc) {

  private val logger = Logger(getClass)

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def addContext(key: String, value: Int) = Action {implicit request =>
    interpreterRepository.save(key, value)
    Ok(Json.obj("result"->"ok"))
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
      "height1" -> height1, "height2" -> height2,
      "deadlineA" -> deadlineA, "deadlineB" -> deadlineB,
      "pubkeyA" -> pubkeyA, "pubkeyB" -> pubkeyB, "hx" -> hx)

    //TODO: read from repo
    //env += interpreterRepository.list

    val prop1 = asBoolValue(new ErgoInterpreterSpecification().compile(env,
      """{
        |  anyOf(Array(
        |    HEIGHT > height1 + deadlineA && pubkeyA,
        |    pubkeyB && blake2b256(taggedByteArray(1)) == hx
        |  ))
        |}""".stripMargin))

    //chain1 script
    val prop1Tree = OR(
      AND(GT(Height, Plus(IntConstant(height1), IntConstant(deadlineA))), pubkeyA),
      AND(pubkeyB, EQ(CalcBlake2b256(TaggedByteArray(1)), hx))
    )

    val prop2 = new ErgoInterpreterSpecification().compile(env,
      """{
        |  anyOf(Array(
        |    HEIGHT > height2 + deadlineB && pubkeyB,
        |    pubkeyA && blake2b256(taggedByteArray(1)) == hx
        |  ))
        |}
      """.stripMargin)//.asBoolValue

    val prop2Tree = OR(
      AND(GT(Height, Plus(IntConstant(height2), IntConstant(deadlineB))), pubkeyB),
      AND(pubkeyA, EQ(CalcBlake2b256(TaggedByteArray(1)), hx))
    )

    //Successful run below:
    //A spends coins of B in chain2
    val ctx1 = ErgoContext(
      currentHeight = height2 + 1,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      boxesToSpend = IndexedSeq(),
      spendingTransaction = null,
      self = fakeSelf)
    val pr = proverA.prove(prop2Tree, ctx1, fakeMessage).get
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
    val pr2 = proverB2.prove(prop1Tree, ctx2, fakeMessage).get
    //verifier.verify(prop1, ctx2, pr2, fakeMessage).get shouldBe true

    if (verifier.verify(prop2Tree, ctx1, pr, fakeMessage).get && verifier.verify(prop1, ctx2, pr2, fakeMessage).get) {
      Ok(Json.obj("test"->"success"))
    } else {
      Ok(Json.obj("test"->"failed"))
    }
  }
}