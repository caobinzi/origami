package com.ambiata
package origami

import FoldM._
import FoldId._
import FoldableM._
import FoldableMS._
import Arbitraries._
import scalaz.{NonEmptyList, State, Id, \/}, Id._
import scalaz.effect.IO
import scalaz.std.list._
import scalaz.std.int._
import scalaz.std.anyVal._
import scalaz.syntax.foldable._
import org.scalacheck._, Prop._, Arbitrary._
import java.io._

object FoldIdSpec extends Properties("FoldId") {

  property("count") = countFold
  property("count unique") = countUniqueFold

  property("any") = anyFold
  property("all") = allFold

  property("anyBreak") = anyBreakFold
  property("allBreak") = allBreakFold

  property("last") = lastFold
  property("latest n") = latestFold

  property("plus") = plusFold
  property("plusBy") = plusByFold
  property("times") = timesFold
  property("timesBy") = timesByFold

  property("maximum") = maximumFold
  property("maximumBy") = maximumByFold
  property("maximumOf") = maximumOfFold
  property("minimum") = minimumFold
  property("minimumBy") = minimumByFold
  property("minimumOf") = minimumOfFold

  property("mean") = meanFold
  property("onlineVariance") = onlineVarianceFold
  property("sha1") = sha1Fold
  property("from state") = stateFold

 // see https://www.cs.ox.ac.uk/jeremy.gibbons/publications/iterator.pdf: "the essence of the Iterator pattern"
 property("line/word/char count") = lineWordCharCount

  type F[A, B] = FoldM[A, Id, B]
  type FInt[A] = F[Int, A]

  def countFold = forAll { list: List[Int] =>
    count[Int].run(list) =? list.size
  }

  def countUniqueFold = forAll { list: List[Int] =>
    countUnique[Int].run(list) =? list.distinct.size
  }

  def anyFold = forAll { list: List[Boolean] =>
    FoldId.any[Boolean](identity _).run(list) =? list.any(identity _)
  }

  def allFold = forAll { list: List[Boolean] =>
    FoldId.all[Boolean](identity _).run(list) =? list.all(identity _)
  }

  def anyBreakFold = forAll { list: List[Boolean] =>
    val iterator = list.toIterator
    val anyTrue = FoldId.any[Boolean](identity _)
    anyTrue.runBreak(iterator)

    val fromTrue = list.dropWhile(_ == false).drop(1)
    iterator.toList =? fromTrue
  }

  def allBreakFold = forAll { list: List[Boolean] =>
    FoldId.all[Boolean](identity _).run(list) =? list.all(identity _)
  }

  def lastFold = forAll { list: List[Int] =>
    last[Int].run(list) =? list.lastOption
  }

  def latestFold = forAll { (list: List[Int], n: SmallInt) =>
    latest[Int](n.value).run(list) =? list.takeRight(n.value)
  }

  def plusFold = forAll { list: List[Int] =>
    plus[Int].run(list) =? list.foldLeft(0)(_ + _)
  }

  def plusByFold = forAll { list: List[String] =>
    plusBy((_: String).size).run(list) =? list.foldLeft(0)(_ + _.size)
  }

  def timesByFold = forAll { list: List[String] =>
    timesBy((_: String).size).run(list) =? list.foldLeft(0)(_ * _.size)
  }

  def timesFold = forAll { list: List[Int] =>
    times[Int].run(list) =? list.foldLeft(0)(_ * _)
  }

  def maximumFold = forAll { list: List[Int] =>
    maximum[Int].run(list) =? list.maximum
  }

  def maximumByFold = forAll { list: List[String] =>
    maximumBy((_: String).size).run(list) =? list.maximumBy(_.size)
  }

  def maximumOfFold = forAll { list: List[String] =>
    maximumOf((_: String).size).run(list) =? list.maximumOf(_.size)
  }

  def minimumFold = forAll { list: List[Int] =>
    minimum[Int].run(list) =? list.minimum
  }

  def minimumByFold = forAll { list: List[String] =>
    minimumBy((_: String).size).run(list) =? list.minimumBy(_.size)
  }

  def minimumOfFold = forAll { list: List[String] =>
    minimumOf((_: String).size).run(list) =? list.minimumOf(_.size)
  }

  def meanFold = forAll { list: List[Double] =>
    if (list.isEmpty) mean[Double].run(list) =? 0.0
    else              mean[Double].run(list) =? list.sum / list.size
  }
  
  def onlineVarianceFold = forAll { nel: NonEmptyList[SmallDouble] =>
    val list = nel.list.map(_.value)
    val count = list.size
    val mean  = list.sum / list.size
    val variance = 
      if (count == 1) 0
      else            (list.map(x => x * x).sum - (list.sum * list.sum) / count) / count
    
    val (c, m, v) = onlineVariance[Double].run(list)
    
    (c ==~ count)    :| "count" &&
    (m ==~ mean)     :| "mean"  && 
    (v ==~ variance) :| "variance"
  }
  
  def lineWordCharCount = forAll { list1: List[String] =>
    val list = List("")
    val countChars: Fold[Char, Int] = count[Char]
    val countWords: Fold[Char, Int] = new FoldM[Char, Id, Int] {
      type S = (Boolean, Int)
      def start = (false, 0)
      def fold = (s: S, t: Char) => {
        val (inWord, count) = s
        (t != ' ', if (!inWord && t != ' ') count + 1 else count)
      }
      def end(s: S) = s._2
    }      
    val countLines = count[String]
    
    val countLinesWordsChars: Fold[String, ((Int, Int), Int)] = 
      countLines zip 
      countWords.nest(_.toList) zip
      countChars.nest(_.toList)
    
    val words = list.flatMap(_.split(" ", -1)).filter(_.nonEmpty)
    val chars = words.flatMap(_.toList)

    countLinesWordsChars.run(list) =? (((list.size, words.size), chars.size))
  }

  def sha1Fold = forAll { sha1Test: Sha1Test =>
    // make as if the string was coming from an input stream
    val is: InputStream = new ByteArrayInputStream(sha1Test.value.getBytes("UTF-8"))
     bytesSha1.into[IO].runS(is).unsafePerformIO =? sha1Test.result
  }
  
  def stateFold = forAll { list: List[Int] =>
    val fold: Fold[Int, Option[Int]] = fromStateEval((i: Int) => State[Int, Int] { count: Int =>
      val above10 = i >= 10
      val newCount = if (above10) count + 1 else count
      (newCount, newCount)
    })(0)
    
    fold.run(list).getOrElse(0) =? list.count(_ > 10)
  }

  /**
   * HELPERS
   */

  case class Sha1Test(value: String, result: String)
  implicit def Sha1TestArbitrary: Arbitrary[Sha1Test] = 
    Arbitrary { Gen.oneOf(
          ("abc",       "a9993e364706816aba3e25717850c26c9cd0d89d")
        , (""   ,       "da39a3ee5e6b4b0d3255bfef95601890afd80709")
        , ("a"*1000000, "34aa973cd4c4daa4f61eeb2bdbad27316534016f")
        ).map { case (v, r) => Sha1Test(v, r) }  
  }

  /** use small doubles for online variance to avoid overflows */
  case class SmallDouble(value: Double) {
    override def toString = value.toString
  }

  implicit def ArbitrarySmallDouble: Arbitrary[SmallDouble] =
    Arbitrary(Gen.choose(-1000.0, 1000.0).map(SmallDouble))

  case class SmallInt(value: Int) {
    override def toString = value.toString
  }

  implicit def ArbitrarySmallInt: Arbitrary[SmallInt] =
    Arbitrary(Gen.choose(-1, 10).map(SmallInt))

}
