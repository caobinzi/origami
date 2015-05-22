package com.ambiata
package origami
package effect

import java.io._
import java.util.UUID

import org.scalacheck.Prop._
import org.scalacheck._, Gen._, Arbitrary._
import FoldIO._
import FoldId._
import FoldM._
import FoldableMS._
import FoldableM._
import scala.io.Codec
import scalaz._
import scalaz.std.list._
import scalaz.syntax.applicative._
import scalaz.syntax.profunctor._
import scalaz.effect._
import Arbitraries._

object FoldIOSpec extends Properties("FoldIO") {

  property("drain to sink") = observeSink
  property("map file + sha1 to other file") = mapAndSha1

  def observeSink = forAll { list: NonEmptyList[Line] =>

    withTempFile { testFile =>
      // sum the size of each string
      val sum: FoldIO[String, Int] =
        plus[Int].contramap((_:String).size).into[IO]

      val sink: Sink[String] =
        fileUTF8LineSink(testFile.getPath)

      val totalAndOutput: FoldIO[Line, Int] =
        sum.observe(sink).contramap[Line](_.value)

      (totalAndOutput.run(list) |@| IO(io.Source.fromFile(testFile)(io.Codec("UTF-8")).getLines)) { (total, lines) =>
        val readLines = lines.toList
        (readLines == list.list.map(_.value)) &&
        (total == list.list.map(_.value.size).sum)
      }
    }
  }

  def mapAndSha1 = forAll { list: NonEmptyList[Line] =>

    withTempDir { dir =>
      val (input, output, sha1Out) = (new File(dir, "input"), new File(dir, "output"), new File(dir, "sha1"))

      // output the results, count the number of lines
      // and compute a sha1 on the result file
      val countAndSha1 =
        (count[Int].into[IO] observe fileUTF8LineSink(output).contramap[Int](_.toString)) <*
        (sha1.into[IO].contramap[Int](i => (i+System.getProperty("line.separator")).getBytes("UTF-8")) pipe fileUTF8LineSink(sha1Out))

      for {
        _          <- fileUTF8LineSink(input).run(list.list.map(_.value))    // save input file
        lines      <- IO(io.Source.fromFile(input)(Codec.UTF8).getLines)     // read lines
        mapped     =  lines.map((_:String).count(_.isDigit))                 // map lines
        count      <- countAndSha1.run(mapped)                               // count, output and sha1
        sha1Lines  <- IO(io.Source.fromFile(sha1Out)(Codec.UTF8).getLines)   // read the sha1
        recomputed <- bytesSha1.into[IO].runS(new FileInputStream(output))   // recompute the sha1
      } yield
        (count =? list.size) :| "count is ok" &&
        (sha1Lines.toList(0) =? recomputed) :| "sha1 is ok"
    }
  }

}
