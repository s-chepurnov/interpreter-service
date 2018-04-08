package controllers

import javax.inject.Inject
import models.EnvironmentDTO
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import repositories.InterpreterRepository
import scapi.sigma.DLogProtocol.ProveDlog
import scorex.crypto.hash.Blake2b256
import sigmastate.Values._
import sigmastate._
import sigmastate.utxo.{ErgoBox, ErgoContext, ErgoInterpreter, ErgoTransaction}
import test.sigmastate.utxo.BoxHelpers.{fakeMessage, fakeSelf}
import test.sigmastate.utxo.{ErgoInterpreterSpecification, ErgoProvingInterpreter}

class InterpreterController @Inject()(cc: ControllerComponents, interpreterRepository: InterpreterRepository)
                                                                                      extends AbstractController(cc) {
  private val logger = Logger(getClass)

  def index = Action { implicit request =>
    Ok(views.html.index()).enableCors
  }

  def getDic = Action { implicit request =>
    val dict = interpreterRepository.getDictionary
    val json: JsValue = Json.obj("dic" -> dict.map(i=>i._1))
    Ok(json).enableCors
  }

  def validate =  Action {implicit request =>
    request.body.asJson.map { json =>
      val scriptText = (json \ "scriptText").as[String]
      val environmentArray = (json \ "env").as[Seq[EnvironmentDTO]]

      val env = Map[String, Any](environmentArray map { a => a.key.toString -> {a.ttype match {
        case "Int" => IntConstant(a.value.toString.toLong)
        case "pubKey" => interpreterRepository.getDictionary(a.value.toString)
      }} }: _*)

      val queryString = env.map(pair => pair._1+"="+pair._2).mkString("?","&","")
      println("validate environment" + queryString)

      var msg = ""
      try {
        println("validate script:" + scriptText)
        asBoolValue(new ErgoInterpreterSpecification().compile(env, scriptText))
      } catch {
        case e: Exception => {msg = e.getMessage}
      }

      if(msg.isEmpty)
        Ok(Json.obj("result"->true)).enableCors
      else
        Ok(Json.obj("result"->false,"msg"->msg)).enableCors
    }.getOrElse {
      BadRequest("Expecting Json data")
    }
  }

  def execute = Action { implicit request =>
    request.body.asJson.map { json =>
      val scriptText = (json \ "scriptText").as[String]
      val environmentArray = (json \ "env").as[Seq[EnvironmentDTO]]
      val contextPreset = (json \ "contextPreset").as[String]

      //val env = Map[String, Any](environmentArray map {  })

      val env : Map[String,Any]= environmentArray.map(a => a.key.toString -> {a.ttype match {
        case "Int" => {/*val i = IamntConstant(a.value.toString.toLong); println(i); i*/100}
        case "pubKey" => {interpreterRepository.getDictionary(a.value.toString)}
      }}).toMap

      val queryString = env.map(pair => pair._1+"="+pair._2).mkString("?","&","")
      println("validate environment" + queryString)

      val compiledScript = asBoolValue(new ErgoInterpreterSpecification().compile(env, scriptText))
      println("executed compiled")
      //execute under Context
      if(contextPreset.equalsIgnoreCase("RING")) {

        val result = proveAndVerifyRingSignature(env, compiledScript)
        Ok(Json.obj("result" -> result)).enableCors

      } else if(contextPreset.equalsIgnoreCase("CROWD")) {

        val result = proveAndVerifyCrowdfunding(env, compiledScript)
        Ok(Json.obj("result" -> result)).enableCors

      } else {

        //not implemented cases are true
        Ok(Json.obj("result" -> true)).enableCors

      }

      Ok(Json.obj("result" -> false)).enableCors
    }.getOrElse {
      BadRequest("Expecting Json data")
    }
  }

  def proveAndVerifyRingSignature(env: Map[String, Any], compiledScript: Values.Value[SBoolean.type]) : Boolean = {
    val verifier = new ErgoInterpreter
    val ctx = ErgoContext(
      currentHeight = 1,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      boxesToSpend = IndexedSeq(),
      spendingTransaction = null,
      self = fakeSelf)

    var result = false;
    for ((k,v) <- env) {
      v match {
        case pubkey: ProveDlog => {
          val prover = interpreterRepository.matcher(pubkey)
          val pr = prover.prove(compiledScript,ctx,fakeMessage).get

          if (verifier.verify(compiledScript, ctx, pr, fakeMessage).get){
            result = true
          } else {
            result = false
          }
        }
      }
    }

    val proverC = new ErgoProvingInterpreter
    if(proverC.prove(compiledScript, ctx, fakeMessage).isFailure) {
      result = result && true
    } else {
      result = result && false
    }
    result
  }

  def proveAndVerifyCrowdfunding(env: Map[String, Any], compiledScript: Value[SBoolean.type]): Boolean = {
    val verifier = new ErgoInterpreter
    val backerProver = new ErgoProvingInterpreter
    val projectProver = new ErgoProvingInterpreter
    val backerPubKey = backerProver.dlogSecrets.head.publicImage
    val projectPubKey = projectProver.dlogSecrets.head.publicImage

    var timeout = IntConstant(100)
//    if(env.contains("timeout")) {
//      timeout = IntConstant((env("timeout")).toString.toLong)
//    }

    var minToRaise = IntConstant(1000)
//    if(env.contains("minToRaise")) {
//      minToRaise = IntConstant((env("minToRaise").toString.toLong))
//    }

    //Third case: height >= timeout
    //project raised enough money but too late...
    val tx3Output1 = ErgoBox(minToRaise.value + 1, projectPubKey)
    val tx3Output2 = ErgoBox(1, projectPubKey)
    val tx3 = ErgoTransaction(IndexedSeq(), IndexedSeq(tx3Output1, tx3Output2))
    val outputToSpend = ErgoBox(10, compiledScript)

    val ctx3 = ErgoContext(
      currentHeight = timeout.value,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      boxesToSpend = IndexedSeq(),
      spendingTransaction = tx3,
      self = outputToSpend)

    //project cant' generate a proof
    if (projectProver.prove(compiledScript, ctx3, fakeMessage).isFailure) {} else {false}

    //backer is generating a proof and it is passing verification
    val proofB = backerProver.prove(compiledScript, ctx3, fakeMessage).get.proof
    if(verifier.verify(compiledScript, ctx3, proofB, fakeMessage).get) {true} else {false}
  }
/*
  def compileScript(script: String) =  Action {implicit request =>
    println(script)
    val env = interpreterRepository.env
    var msg = ""
    try {
      asBoolValue(new ErgoInterpreterSpecification().compile(env, script))
    } catch {
      case e: Exception => {msg = e.getMessage}
    }

    if(msg.isEmpty)
      Ok(Json.obj("result"->true)).enableCors
    else
      Ok(Json.obj("result"->false,"msg"->msg)).enableCors
  }

  def executeOld = Action {implicit request =>
    val inputJson = request.body

    val script: String = inputJson.asText.get //get from inputJson

    val verifier = new ErgoInterpreter
    val proverB = new ErgoProvingInterpreter
    val projectProver = new ErgoProvingInterpreter
    val backerPubKey = proverB.dlogSecrets.head.publicImage
    val projectPubKey = projectProver.dlogSecrets.head.publicImage

    val timeout = IntConstant(100)
    val minToRaise = IntConstant(1000)

    val env = Map(
      "timeout" -> 100,
      "minToRaise" -> 1000,
      "backerPubKey" -> backerPubKey,
      "projectPubKey" -> projectPubKey
    )

    val compiledScript = asBoolValue(new ErgoInterpreterSpecification().compile(env, script))

    //if minTpRaise contains in map ELSE 1000
    val tx3Output1 = ErgoBox(minToRaise.value + 1, projectPubKey)
    val tx3Output2 = ErgoBox(1, projectPubKey)
    val tx3 = ErgoTransaction(IndexedSeq(), IndexedSeq(tx3Output1, tx3Output2))
    val outputToSpend = ErgoBox(10, compiledScript)

    val ctx = ErgoContext(
      currentHeight = timeout.value,
      lastBlockUtxoRoot = AvlTreeData.dummy,
      boxesToSpend = IndexedSeq(),
      spendingTransaction = tx3,
      self = outputToSpend)

    val proofB = proverB.prove(compiledScript, ctx, fakeMessage).get
    if (verifier.verify(compiledScript, ctx, proofB, fakeMessage).get) {
      Ok(Json.obj("result"->true)).enableCors
    } else {
      Ok(Json.obj("result"->false)).enableCors
    }


    /*val proofB = proverB.prove(compiledScript, ctx, fakeMessage).get.proof

    if (projectProver.prove(compiledScript, ctx, fakeMessage).isFailure &&
        verifier.verify(compiledScript, ctx, proofB, fakeMessage).get) {
      Ok(Json.obj("result"->true)).enableCors
    } else {
      Ok(Json.obj("result"->false)).enableCors
    }*/
  }


  def setContext(name: String) = Action { implicit request =>
    interpreterRepository.put(name)
    Ok(Json.obj("result" -> true)).enableCors
  }

  def getContext = Action { implicit request =>
    implicit val environmentDTOWrites = new Writes[EnvironmentDTO] {
      def writes(env: EnvironmentDTO) = Json.obj(
        env.value match {
          //case p: ProveDlog => env.key.toString -> new String(env.value.asInstanceOf[ProveDlog].bytes, StandardCharsets.UTF_8)
          //case p: ByteArrayConstant => env.key.toString -> env.value.asInstanceOf[ByteArrayConstant].value
          case _ => env.key.toString -> env.value.toString
        }
      )
    }

    val env = interpreterRepository.env.map(i => EnvironmentDTO(i._1, i._2))
    Ok(Json.toJson(env)).enableCors
  }
*/

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
      Ok(Json.obj("result"->true)).enableCors
    } else {
      Ok(Json.obj("result"->false)).enableCors
    }
  }

  def asBoolValue(v: Value[SType]): Value[SBoolean.type] = v.asInstanceOf[Value[SBoolean.type]]

  implicit class RichResult (result: Result) {
    def enableCors =  result.withHeaders(
      "Access-Control-Allow-Origin" -> "*"
      , "Allow" -> "*"
      , "Access-Control-Allow-Methods" -> "OPTIONS, GET, POST, PUT, DELETE, HEAD"
      , "Access-Control-Allow-Headers" -> "Accept, Content-Type, Origin, X-Json, X-Prototype-Version, X-Requested-With, Referer, User-Agent"
      , "Access-Control-Allow-Credentials" -> "true"
    )
  }

  implicit val environmentReads: Reads[EnvironmentDTO] = (
    (JsPath \ "key").read[String] and
      (JsPath \ "value").read[String] and
      (JsPath \ "type").read[String]
    )(EnvironmentDTO.apply _)
}
