## Iterator[String]

An `Iterator[String]` is what you get after invoking `scala.io.Source.fromFile(file).getLines` (see *[foreach school](foreach.md)*). When you import implicits from `com.ambiata.origami.FoldableM` you can turn this `Iterator` into a `FoldableM` and apply `FoldMs` to it

#### State folding

Let's go back to the example of counting the number of lines and totalLineSize of a file
```scala
import com.ambiata.origami._
import FoldId._
import FoldM._
import FoldableM._
import scalaz._, Scalaz._

val source = scala.io.Source.fromFile("file.txt")

val totalSize: Fold[String, Int] =
  fromMonoidMap[String](_.size)

(count <*> totalSize).run(source)
```

This solves the "composition" problem because it is now very easy to develop and test a new piece of folding, `totalSize`, and add it to the current processing, `count`.

This is however not very clean because:

 - the fold is executed right away, as a side-effect
 - there is no resource management

#### The IO monad

We need, at least, to handle side effects. In order to do this we can execute our fold in the `IO` monad:
```scala
val totalSize: Fold[String, Int] =
  fromMonoidMap[String](_.size)

val result: IO[(Int, Int)] =
  (count <*> totalSize).into[IO].run(source)
```

This is better, now we need to close the source when we are done with it.

#### SafeTIO

```scala
val totalSize: Fold[String, Int] =
  fromMonoidMap[String](_.size)

val result: SafeTIO[(Int, Int)] =
  (count <*> totalSize).into[SafeT[IO, ?]].run(source)
```

`SafeTIO` is a type alias for `SafeT[IO, ?]` (see [Type aliases](typealiases.md) for more definitions) and is just a small layer on top of `IO` to register "finalizers" which will execute when an `IO` action executes whether it succeeds or not (see [SafeT](safet.md)).

The result is a `SafeTIO[(Int, Int)]` which we can run to get a proper `IO` value:
```scala
 val ioResult: IO[(Int, Int)] =
   result.run
```
