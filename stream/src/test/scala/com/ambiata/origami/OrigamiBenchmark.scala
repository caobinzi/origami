package com.ambiata.origami

import org.scalameter._
import FoldM._, FoldableM._
import effect._, SafeT._
import FoldIO._
import FoldTask._
import FoldSafeT._
import stream.FoldableProcessM._
import stream.FoldProcessM._
import scalaz._, Scalaz._
import scalaz.effect._
import scalaz.concurrent._

class OrigamiBenchmark extends PerformanceTest {
  val TEST_FILE = "target/test-file-medium"

  import reporting._
  def persistor: Persistor = new persistence.SerializationPersistor
  def warmer: Warmer = Warmer.Default()
  def aggregator: Aggregator = Aggregator.average
  def measurer: Measurer = new Measurer.IgnoringGC with Measurer.PeriodicReinstantiation with Measurer.OutlierElimination with Measurer.RelativeNoise
  def executor: Executor = new execution.SeparateJvmsExecutor(warmer, aggregator, measurer)
  def tester: RegressionReporter.Tester = RegressionReporter.Tester.OverlapIntervals()
  def historian: RegressionReporter.Historian = RegressionReporter.Historian.ExponentialBackoff()
  def reporter: Reporter = Reporter.Composite(
    new RegressionReporter(tester, historian),
    HtmlReporter(embedDsv = true)
  )

  def save(size: Int) = {
    val name = TEST_FILE+"_"+size
    val writer = new java.io.FileWriter(name)
    (1 to size).toIterator.foreach(i => writer.write(i.toString+"\n"))
    writer.close
    name
  }

  val factor = 100000
  val sizes = Gen.range("size")(10, 50, 100).map(_ * factor)

  lazy val files = sizes.map(save)
  lazy val linesR = sizes.map(save).map(scalaz.stream.io.linesR)

  lazy val sum = fromMonoid[Int]

  performance of "foldm on BufferedSource" in {
    using(files) in { file =>
      measure method "base case - left fold" in {
        lines(file)(_.sum)
      }
    }
    using(files) in { file =>
      measure method "foldm in Id" in {
        lines(file)(sum.run[Iterator[Int]])
      }
    }
    using(files) in { file =>
      measure method "foldm in IO" in {
        lines(file)(ls => sum.into[IO].run[Iterator[Int]](ls).unsafePerformIO)
      }
    }
    using(files) in { file =>
      measure method "foldm in Task" in {
        lines(file)(ls => sum.into[Task].run[Iterator[Int]](ls).run)
      }
    }
    using(files) in { file =>
      measure method "foldm in SafeTIO" in {
        lines(file)(ls => sum.into[SafeTIO].run[Iterator[Int]](ls).run.unsafePerformIO)
      }
    }
    using(files) in { file =>
      measure method "foldm in SafeTTask" in {
        lines(file)(ls => sum.into[SafeTTask].run[Iterator[Int]](ls).run.run)
      }
    }
  }
  performance of "foldm on Process" in {
    using(linesR) in { lines =>
      measure method "foldm in Task" in {
        sum.into[Task].run(lines.map(_.toInt)).run
      }

      measure method "foldm in SafeTTask" in {
        sum.into[SafeTTask].run(lines.map(_.toInt)).run
      }
    }
  }

  def lines[A](file: String)(f: Iterator[Int] => A): A = {
    val source = scala.io.Source.fromFile(file)
    try f(source.getLines.map(_.toInt))
    finally source.close
  }

}
