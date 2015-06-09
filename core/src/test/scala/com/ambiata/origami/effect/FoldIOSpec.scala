package com.ambiata
package origami
package effect

import java.io._
import java.util.UUID

import org.scalacheck.Prop._
import org.scalacheck._, Gen._, Arbitrary._
import FoldIO._
import SafeT._
import FoldId._
import FoldM._
import FoldableSafeTM._
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
  property("a sink must be closed even if folding throws an exception") = sinkSafety

  def observeSink = forAllNoShrink { list: NonEmptyList[Line] =>

    withTempFile { testFile =>
      // sum the size of each string
      val sum: FoldIO[String, Int] =
        plus[Int].contramap((_:String).size).into[SafeTIO]

      val sink: Sink[String] =
        fileUTF8LineSink(testFile.getPath)

      val totalAndOutput: FoldIO[Line, Int] =
        sum.observe(sink).contramap[Line](_.value)

      (totalAndOutput.run(list).run |@| IO(io.Source.fromFile(testFile)(io.Codec("UTF-8")).getLines)) { (total, lines) =>
        val readLines = lines.toList
        (readLines == list.list.map(_.value)) &&
        (total == list.list.map(_.value.size).sum)
      }
    }
  }

  def mapAndSha1 = forAllNoShrink { list: NonEmptyList[Line] =>

    withTempDir { dir =>
      val (input, output, sha1Out) = (new File(dir, "input"), new File(dir, "output"), new File(dir, "sha1"))

      // output the results, count the number of lines
      // and compute a sha1 on the result file
      val countAndSha1 =
        (count[Int].into[SafeTIO] observe fileUTF8LineSink(output).contramap[Int](_.toString)) <*
        (sha1.into[SafeTIO].contramap[Int](i => (i+System.getProperty("line.separator")).getBytes("UTF-8")) pipe fileUTF8LineSink(sha1Out))

      for {
        _          <- fileUTF8LineSink(input).run(list.list.map(_.value)).run           // save input file
        lines      <- getLinesFrom(input)                               // read lines
        mapped     =  lines.map((_:String).count(_.isDigit))                        // map lines
        count      <- countAndSha1.run(mapped).run                                      // count, output and sha1
        sha1Lines  <- getLinesFrom(sha1Out)                             // read the sha1
        recomputed <- bytesSha1.into[SafeTIO].run(new FileInputStream(output)).run      // recompute the sha1
      } yield
        (count ?= list.size) :| "count is ok" &&
        (sha1Lines.toList(0) ?= recomputed) :| "sha1 is ok"
    }
  }

  def sinkSafety = forAllNoShrink { list: NonEmptyList[Line] =>
    withTempFile { testFile =>
      // run a sum fold that's throwing an exception
      val sum: FoldIO[String, Int] =
        plus[Int].contramap[String](s => {sys.error("boom"); 1}).into[SafeTIO]

      val sink: Sink[String] =
        fileUTF8LineSink(testFile.getPath)

      val totalAndOutput: FoldIO[Line, Int] =
        sum.observe(sink).contramap[Line](_.value)

      // if resources were not properly closed
      // this would fail with "Too many open files in system"  
      (1 to 200) foreach { i =>
        totalAndOutput.run(list).run
      }
      IO(true)
    }
  }

  def getLinesFrom(f: File): IO[Iterator[String]] =
    IO(io.Source.fromFile(f)(Codec.UTF8).getLines)

}
