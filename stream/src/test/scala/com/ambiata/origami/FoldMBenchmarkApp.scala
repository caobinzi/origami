package com.ambiata
package origami

import com.google.caliper._
import FoldM._
import FoldableM._
import stream.FoldableProcessM._
import stream.FoldProcessM._
import scalaz._, Scalaz._
import scalaz.concurrent._
import scalaz.stream._
import org.scalacheck._

object FoldMBenchmarkApp extends App {
  Runner.main(classOf[FoldMBenchmark], args)
}

class FoldMBenchmark extends SimpleScalaBenchmark {
  val TEST_FILE = "target/test-file-medium"
  
  val write = {
  	val writer = new java.io.FileWriter(TEST_FILE)
  	(1 to 10000).toIterator.foreach(i => writer.write(i.toString+"\n"))
  	writer.close
  }

  val sum: Fold[Int, Int] = 
    fromMonoid[Int]

  def lines[A](reps: Int)(f: Iterator[Int] => A): A = {
    repeat(reps) {
      val source = scala.io.Source.fromFile(TEST_FILE)
      try f(source.getLines.map(_.toInt))
      finally source.close 
    }
  }

  def timeSourceIteratorLeftFold(reps: Int): Unit = {
    lines(reps)(_.sum)
  }

  def timeSourceIteratorFoldM(reps: Int): Unit =  {
    lines(reps)(sum.run[Iterator])
  }

  def timeSourceIteratorTaskFoldM(reps: Int): Unit =  {
    lines(reps)(sum.into[Task].run[Iterator])
  }

  def timeProcessFoldM(reps: Int): Unit =  {
    val lines = io.linesR(TEST_FILE)
    repeat(reps)(
      sum.into[Task].run[Process[Task, ?]](lines.map(_.toInt)).run
    )
  }

}

trait SimpleScalaBenchmark extends SimpleBenchmark {

  // helper method to keep the actual benchmarking methods a bit cleaner
  // your code snippet should always return a value that cannot be "optimized away"
  def repeat[@specialized A](reps: Int)(snippet: => A) = {
    val zero = 0.asInstanceOf[A] // looks weird but does what it should: init w/ default value in a fully generic way
    var i = 0
    var result = zero
    while (i < reps) {
      val res = snippet
      if (res != zero) result = res // make result depend on the benchmarking snippet result
      i = i + 1
    }
    result
  }

}
