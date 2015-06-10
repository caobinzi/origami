# <center><img src="http://upload.wikimedia.org/wikipedia/commons/f/fd/Origami-crane.jpg" alt="fold" width="150px"/> Origami </center>
<br/>

The ***origami*** project provides "Monadic folds" to process streams of data in a composable fashion.

With monadic folds you can:

 - accumulate state, for example count the number of elements or compute a hash value

 - output results to a file or a database as you compute them

 - display the end value on the console

Moreover folds are composable:

 - you can implement and test folds independently from the data stream they will be used on

 - you can run two folds at the same time without having to make two passes over the data stream

 - you can add side-effects like writing results to a file later on if you need to

Finally, side-effecting folds like `Sinks` are operating inside a `SafeT` monad to make sure that resources are always released, even when there are exceptions.

*You can read more about the motivations for the library in ["Foreach school"](foreach.md)*

## FoldM and FoldableM

The ***origami*** library provides 2 abstractions for "folding" data streams: `FoldM` and `FoldableM`.

A `FoldM[M, T, U]` represents the operation you want to execute on a data stream:

 - a internal `type S` representing the accumulated state
 - a `start` method returning an initial `M[S]` element
 - a `fold` method `(s: S, t: T) => S` describing how to "fold" the streamed elements with the previous state
 - a `end(s: S)` method returning `M[U]` and finalizing the computation

A `FoldableM[M, F, T]` represents a stream `F` of elements of type `T`. It can use a `FoldM[M, T, U]` to compute a final value `M[U]`.

This is all very abstract so let's have a look at a simple example:

```scala
import com.ambiata.origami._, FoldableM._

def count[T] = new FoldM[Id, T, Int] {
  type S = Int
  def start = 0
  def fold = (s: S, t: T) => s + 1
  def end(s: S) = s
}

val list: List[Int] = List(1, 2, 3)

FoldableM[Id, List].foldm(list)(count) == 3

// or simply
count.run(list) == 3
```

In the example above `count` is a `FoldM` where `M` is simply `Id`. Its internal state is an `Int` and is simply returned at the end of the fold.

`List` is a Scalaz `Foldable` and there is a `FoldableM` instance available for any `Foldable` when `M` has a `Bind` instance (trivial here since `M = Id`), so we can "run" the `count` fold on the list of ints.

## Features

Besides those 2 traits the rest of the library provides:

 - combinators for `FoldM`s

 - type aliases for specialized folds, like `Fold[T, U]` when `M = Id`

 - standard `Fold[T, U]` folds for statistics, random values generation or hashes

 - a way to stop the folding of values when a given state has been reached

 - a `FoldableM` instance for `Iterator[T]` to work with lines retrieved from `scala.io.Source`

 - a `FoldableM` instance for `java.io.InputStream` to work with regular Java streams

 - a `FoldableM` instance for`Process[M, T]` to work with scalaz-stream

 - ways to create "side-effecting" folds in order to write files (`SinkM` folds)

 - a `SafeT` monad to handle resources management
