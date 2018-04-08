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

  /**
    * user defined script is executed
    * context and environment is prepared artificially
    * @return
    */
  def execute = Action { implicit request =>
    request.body.asJson.map { json =>
      val scriptText = (json \ "scriptText").as[String]
      val environmentArray = (json \ "env").as[Seq[EnvironmentDTO]]
      val contextPreset = (json \ "contextPreset").as[String]

      if(contextPreset.equalsIgnoreCase("RING")) {
        val proverA = new ErgoProvingInterpreter
        val proverB = new ErgoProvingInterpreter
        val proverC = new ErgoProvingInterpreter
        val verifier = new ErgoInterpreter

        val pubkeyA = proverA.dlogSecrets.head.publicImage
        val pubkeyB = proverB.dlogSecrets.head.publicImage

        val env = Map("pubkeyA" -> pubkeyA, "pubkeyB" -> pubkeyB)
        val compiledScript = asBoolValue(new ErgoInterpreterSpecification().compile(env, scriptText))

        val ctx = ErgoContext(
          currentHeight = 1,
          lastBlockUtxoRoot = AvlTreeData.dummy,
          boxesToSpend = IndexedSeq(),
          spendingTransaction = null,
          self = fakeSelf)

        val prA = proverA.prove(compiledScript, ctx, fakeMessage).get
        val prB = proverB.prove(compiledScript, ctx, fakeMessage).get

        if (verifier.verify(compiledScript, ctx, prA, fakeMessage).get &&
          verifier.verify(compiledScript, ctx, prB, fakeMessage).get &&
          proverC.prove(compiledScript, ctx, fakeMessage).isFailure ) {
          Ok(Json.obj("result" -> true)).enableCors
        } else {
          Ok(Json.obj("result" -> false)).enableCors
        }


      } else if(contextPreset.equalsIgnoreCase("CROWD")) {

        val verifier = new ErgoInterpreter
        val backerProver = new ErgoProvingInterpreter
        val projectProver = new ErgoProvingInterpreter
        val backerPubKey = backerProver.dlogSecrets.head.publicImage
        val projectPubKey = projectProver.dlogSecrets.head.publicImage
        val timeout = IntConstant(100)
        val minToRaise = IntConstant(1000)
        val env = Map(
          "timeout" -> 100,
          "minToRaise" -> 1000,
          "backerPubKey" -> backerPubKey,
          "projectPubKey" -> projectPubKey
        )

        //scriptText from user front-end
        val compiledScript = asBoolValue(new ErgoInterpreterSpecification().compile(env,scriptText))

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

        val proofB = backerProver.prove(compiledScript, ctx3, fakeMessage).get.proof

        if (projectProver.prove(compiledScript, ctx3, fakeMessage).isFailure &&
          verifier.verify(compiledScript, ctx3, proofB, fakeMessage).get) {
          Ok(Json.obj("result" -> true)).enableCors
        } else {
          Ok(Json.obj("result" -> false)).enableCors
        }

      } else {
        //not implemented cases are true
        Ok(Json.obj("result" -> true)).enableCors
      }

      //Ok(Json.obj("result" -> false)).enableCors
    }.getOrElse {
      BadRequest("Expecting Json data")
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
