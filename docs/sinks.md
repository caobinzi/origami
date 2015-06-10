## SinkM

Not only we need to [read from streams](iterator.md) but we also need to write elements and / or results out.

A special case of `FoldM` is `SinkM`. A `SinkM` is a fold which only has side effects, its type is `type SinkM[M, T] = FoldM[M, T, Unit]`. A `SinkM` can be created to output data to a file:
```scala
import com.ambiata.origami._ , FoldM._, FoldableM._, effect.FoldIO._

val sink: SinkM[IO, String] =
  fileUTF8LineSink("output.txt")

sink.contramap[Int](_.toString).run(List(1, 2, 3))
```

In the example above we create a `File` sink which will accept Strings and output them to a file. Note that we need to `contramap` the sink to transform each `Int` coming from the list into a `String`.

More generally sinks are generally used in conjunction with other folds to:

 1. output the streamed elements
 2. output the successive states
 3. output the final values

### Output streamed elements

The `observe` method (or `<*`) can be used to output the elements streamed to a given fold:
```scala
val countElements: Fold[String, Int] =
  count[String]

// the countElements fold needs to be
// put in IO in order to be observed by a file sink
val fold: Fold[SafeTIO, String, Int] =
  (countElements.into[SafeTIO] <* fileUTF8LineSink("output.txt"))

// will output 3 lines in the output.txt file: "a", "b", "c"
fold.run(List("a", "b", "c")).unsafePerformIO == 3
```

You can notice that the `M` monad used here is `SafeTIO` so that the output file gets closed even if there is an exception when doing the folding (see [SafeT](safet.md) for more information).

### Output state values

The `observeState` method (or `<<*`) can be used to output the successive states of a given fold:
```scala
val fold: Fold[SafeTIO, String, Int] =
  (countElements.into[SafeTIO] <<* fileUTF8LineSink("output.txt").contramap[Int](_.toString))

// will output 3 lines in the output.txt file: "0", "1", "2"
fold.run(List("a", "b", "c")).run.unsafePerformIO == 3
```

Since the "state" we observe here is of type `Int` we need to `contramap` the file sink in order to print Strings.

As you can see the "states" we observe here are all the state values *before* the call to the `fold` method. If you want to observe the states *after* the `fold` method you need to use `observeNextState` (or `<<<*`):
```scala
val fold: Fold[SafeTIO, String, Int] =
  (countElements.into[SafeTIO] <<<* fileUTF8LineSink("output.txt").contramap[Int](_.toString))

// will output 3 lines in the output.txt file: "1", "2", "3"
fold.run(List("a", "b", "c")).run.unsafePerformIO == 3
```

### Output the last value

The last value `u: U` computed by a `FoldM[M, T, U]` is of type `M[U]`. If you want to output the last value you can simply rely on the `M` monad and the `mapFlatten` method

```scala
val fold: Fold[SafeTIO, String, Int] =
  (countElements.into[SafeTIO] <* fileUTF8LineSink("output.txt")).mapFlatten { last: Int =>
    fileUTF8LineSink("count.txt").contramap[Int](_.toString).run1(last)
  }

// will output 3 lines in the output.txt file: "a", "b", "c"
// and one line in the count.txt file: "3"
fold.run(List("a", "b", "c")).run.unsafePerformIO == 3
```
