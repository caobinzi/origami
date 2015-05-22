package com.ambiata
package origami
package stream

import FoldM._
import FoldId._
import FoldProcessM._
import stream.FoldableProcessM._
import scodec.bits.ByteVector
import scalaz.effect.IO
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.{Sink, Process}
import org.scalacheck._, Prop._
import Arbitraries._
import scala.io.Source

class FoldProcessMSpec extends Properties("Process Task folds") {

  property("fromSink - creates a fold from a Sink") = observeSink

  type F[A, B] = FoldM[Task, A, B]

  def observeSink = forAll { list: NonEmptyList[Line] =>

    val listProcess: Process[Task, Line] =
      Process.emitAll(list.list).evalMap(Task.now)

    withTempFile { testFile =>

      // sum the size of each string
      val sum: Fold[String, Int] { type S = Int } =
        plus[Int].contramap((_:String).size)

      var writtenLines = 0
      val sink: F[(Int, String), Unit] =
        FoldProcessM.fromSink(scalaz.stream.io.fileChunkW(testFile.getPath)).contramap[(Int, String)] { case (i, s) =>
          val line = s"sum=$i,string=$s"
          writtenLines += 1
          ByteVector((line+"\n").getBytes("UTF-8"))
        }

      val totalAndOutput: F[Line, Int] =
        (sum.into[Task] <<* sink).contramap[Line](_.value)

      (IO(totalAndOutput.run[ProcessTask[Line]](listProcess).run) |@| IO({Thread.sleep(100); Source.fromFile(testFile)(io.Codec("UTF-8")).getLines})) { (total, lines) =>
        val readLines: List[String] = lines.toList
        val values: List[String] = list.list.map(_.value)
        val sums = values.scanLeft(0)(_ + _.size)
        val expected = (values zip sums) map { case (s, i) => s"sum=$i,string=$s" }

        (readLines ?= expected) &&
        (writtenLines ?= expected.size) &&
        (total ?= values.map(_.size).sum)
      }
    }
  }

}
