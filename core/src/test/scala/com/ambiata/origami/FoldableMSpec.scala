package com.ambiata
package origami

import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._
import FoldM._
import FoldableM._
import FoldId._
import Arbitraries._
import scalaz.{Apply, Cobind, Compose, Id, Reducer, Monoid, \/-, -\/}, Id._
import scalaz.std.list._
import scalaz.std.anyVal._
import scalaz.syntax.monad._
import scalaz.syntax.traverse._
import com.ambiata.disorder.NaturalIntSmall
import java.io._

object FoldableMSpec extends Properties("FoldableM") {

  property("break law for FoldableM - Foldable instance")     = foldableFoldableMBreakLaw
  property("break law for FoldableM - Iterator instance")     = iteratorFoldableMBreakLaw
  property("break law for FoldableM - input stream bytes instance") = inputStreamBytesFoldableMBreakLaw
  property("break law for FoldableM - input stream string instance") = inputStreamStringFoldableMBreakLaw


  def foldableFoldableMBreakLaw = forAll { (list: List[Int], fold: F[Int, String] { type S = Int }) =>
    FoldableM.laws.breakLaw(FoldableIsFoldableM[Id, List, Int], (i: Int) => i % 2 == 0, fold, list)
  }

  def iteratorFoldableMBreakLaw = forAll { (list: List[Int], fold: F[Int, String] { type S = Int }) =>
    FoldableM.laws.breakLaw(IteratorIsFoldableM[Id, Int], (i: Int) => i % 2 == 0, fold, list.toIterator)
  }

  def inputStreamBytesFoldableMBreakLaw = forAll { (s: String, fold: F[Bytes, String] { type S = Int }) =>
    val inputStream = new ByteArrayInputStream(s.getBytes("UTF-8"))
    FoldableM.laws.breakLaw(inputStreamAsFoldableMS[Id, InputStream](4096), (i: Int) => i % 2 == 0, fold, inputStream)
  }

  def inputStreamStringFoldableMBreakLaw = forAll { (s: String, fold: F[String, String] { type S = Int }) =>
    val inputStream = new ByteArrayInputStream(s.getBytes("UTF-8"))
    FoldableM.laws.breakLaw(inputStreamAsFoldableStringMS[Id, InputStream](4096), (i: Int) => i % 2 == 0, fold, inputStream)
  }

}
