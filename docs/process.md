## Process[Task, A]

[Scalaz Stream](https://github.com/scalaz/scalaz-stream) provides powerful operators to create streams of elements and combinators to modify them or to compute state in an asynchronous manner if necessary.

There is a `FoldableM` instance for `Process[M, A]` in the `com.ambiata.origami.stream.FoldableProcessM` object.

This means that you can reuse all the `FoldIds` for statistics (`count`, `mean`, `variance`, ...), or side-effecting folds (for creating reports for example) on Processes.

Moreover it is possible to transform the existing Scalaz-stream `Sink`s into `SinkM` with the `com.ambiata.origimi.stream.FoldProcessM.fromSink` method. This way you can also use the scalaz-stream api for creating sinks and add those sinks to origami's `Folds`.

Let's have a look at one example, where we:

 - sum the size of strings from a Source

 - we output each string and its size to a File

 - we return the total at the end

```scala
import com.ambiata.origami._, Origami._
import stream.FoldProcessM._
import stream.FoldableProcessM._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process
import scodec.bits.ByteVector

val listProcess: Process[Task, String] =
  Process.emitAll(List("a", "bb", "ccc")).evalMap(Task.now)

// add the size of each string
val sum: Fold[String, Int] { type S = Int } =
  plus[Int].contramap((_:String).size)

// save the current string and current sum to a file
// use a Scalaz Sink to create an origami Sink
val sink =
  fromSink(scalaz.stream.io.fileChunkW("file.txt")).contramap[(Int, String)] { case (i, s) =>
    val line = s"sum=$i,string=$s"
    ByteVector((line+"\n").getBytes("UTF-8"))
  }

// run the sum, observe value and state
val totalAndOutput: FoldM[SafeTTask, String, Int] =
  (sum.into[SafeTTask] <<* sink)

// run the fold and get the total
val total = totalAndOutput.run(listProcess).run
```

This is not very different from previous examples, the main novelty here is the use of a Scalaz stream `Sink` to output elements and state.
