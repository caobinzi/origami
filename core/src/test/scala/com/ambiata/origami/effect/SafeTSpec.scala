package com.ambiata
package origami
package effect

import org.scalacheck._, Gen._, Arbitrary._, Prop._
import scala.io.Codec
import scalaz._, Scalaz._
import scalaz.effect._
import SafeT._
import disorder._

object SafeTSpec extends Properties("SafeTSpec") {

  property("an added finalizer must always be called") =
    addedFinalizer

  property("finalizers must be called in the outermost order") =
    callOrder

  def addedFinalizer = forAllNoShrink { value: Value[Int] =>
    val finalizer = TestFinalizer()
    val safeT = monad.point(value.value()) `finally` finalizer.run

    safeT.run.unsafePerformIO
    val wasCalled = finalizer.called

    wasCalled :| "the finalizer was called: "+wasCalled
  }

  def callOrder = forAllNoShrink { values: List[Value[Int]] =>
    var order = new collection.mutable.ListBuffer[String]
    def finalizer(i: Int) = IO { order.append("finalizer "+i); () }

    val safeT = values.zipWithIndex.foldLeft(point(Value.value(0))) { case (res, (cur, i)) =>
      res >> (point(cur) `finally` finalizer(i))
    }
    safeT.run.unsafePerformIO

    order.toList ?= values.zipWithIndex.reverse.map (_._2) map ("finalizer "+_)
  }

  /**
   * HELPERS
   */

  def monad = Monad[SafeT[IO, ?]]

  def point[A](value: Value[A]) =
     monad.point(value.value)

  /** class of values which can throw exceptions */
  case class Value[A](value: () => A, originalValue: A, throwsException: Boolean) {
    def run: IO[A] =
      IO(value())

    override def toString = "value: "+ originalValue + (if (throwsException) " - throws Exception" else "")
  }

  object Value {
    def value[A](a: A): Value[A] =
      Value(() => a, a, throwsException = false)
  }

  implicit def ArbitraryValue[A : Arbitrary]: Arbitrary[Value[A]] =
    Arbitrary {
      for {
        b <- arbitrary[Boolean]
        a <- arbitrary[A]
      } yield Value(() => if (b) { throw new java.lang.Exception("no value!"); a } else a, a, b)
    }

  /** class of values which can never throw exceptions */
  case class ValueOk[A](value: A) {
    def run: IO[A] =
      IO(value)

    override def toString = "value: "+ value
  }

  implicit def ArbitraryValueOk[A : Arbitrary]: Arbitrary[ValueOk[A]] =
    Arbitrary(arbitrary[A].map(ValueOk(_)))

}

case class TestFinalizer(var called: Boolean = false) extends Finalizer[IO] {
  def run: IO[Unit] = IO { called = true; () }
}
