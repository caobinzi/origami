## SafeT

`SafeT[M, ?]` is a Monad transformer around a given monad `M`. It just adds some resource management functionality to, e.g. `IO`.

The simplest way of creating a `SafeT[M, ?]` object is to use the `finally` method:
```scala
import com.ambiata.origami._, Origami._
import effect.SafeT
import scalaz.effect.IO
import scala.io._

def readLines(file: BufferedSource): IO[Iterator[String]] = ???

val source = scala.io.Source.fromFile("test.txt")

val read: SafeT[IO, Iterator[String]] =
  readLines(source) `finally` IO(source.close)
```

When you `run` the `read` action you get an `IO` action which will guarantee to close the source whether an exception occurs or not. If you chain several `SafeT` actions, each finalizer will be guaranteed to be called in turn.

## Exceptions

What happens when there are exceptions? During the main action? During the execution of a finalizer?

When you call the `run` method you need to satisfy 2 constraints:

 - `M` needs to have a `Monad` instance

 - `M` needs to have a `Catchable` instance

 Then, when everything runs (including the finalizers) if there is any exception, it gets reported through the `fail` method of the `Catchable` trait. This means that:

  - if your monad `M` is `IO` you will get an exception on execution with `unsafePerformIO`

  - if you monad `M` is `Task` you get a chance to intercept that exception with `attemptRun`

An another solution is to call the `attemptRun` method directly on `SafeT[M, ?]`:
```  
val lines: IO[(Throwable \/ Int, Option[FinalizersException])] =
  read.map(_.size).attemptRun
```

The `attemptRun` method returns either:

 - either a value of type `Int` or a `Throwable` if the value can't be produced

 - an `Option` possibly containing a `FinalizersException` listing exceptions thrown by the finalizers
