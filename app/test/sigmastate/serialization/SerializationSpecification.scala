package test.sigmastate.serialization/*
import com.google.inject.matcher.Matchers
import sigmastate.SType
import sigmastate.Values._
import sigmastate.serialization.ValueSerializer

trait SerializationSpecification extends PropSpec
  with PropertyChecks
  with GeneratorDrivenPropertyChecks
  with TableDrivenPropertyChecks
  with Matchers {

  protected def roundTripTest[V <: Value[_ <: SType]](v: V): Assertion = {
    val bytes = ValueSerializer.serialize(v)
    val t = ValueSerializer.deserialize(bytes)
    t shouldBe v
  }

  protected def predefinedBytesTest[V <: Value[_ <: SType]](bytes: Array[Byte], v: V): Assertion = {
    ValueSerializer.deserialize(bytes) shouldBe v
  }
}*/