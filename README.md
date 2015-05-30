# Origami

Monadic folds

# WORK IN PROGRESS, NOT READY FOR GENERAL USE!!!

<img src="http://upload.wikimedia.org/wikipedia/commons/f/fd/Origami-crane.jpg" alt="fold" width="400px"/>

## Presentation

When processing data streams it is often necessary to cumulate different "effects":

 - accumulate some state (for example counting the number of elements, or computing a hash value)
 - output results to a file or a database
 - display the end value on the console
 
Moreover we want to be able to:

 - develop these features in a composable way. It should be possible to describe and test the counting of elements (or other piece of statistics like mean/variance) independently from its output to a file
 
 - do all sorts of computations and side-effects in **one** traversal (not have to read a file twice to output the number of lines and the MD5 hash)
 
### `FoldM` and `FoldableM`
 
The ***origami*** library offers 2 abstractions to do this: `FoldM` and `FoldableM`. 

Basically a `FoldM[M, T, U]` instance has:

 - a internal `type S` representing the accumulated state
 - a `start` method returning an initial `M[S]` element
 - a `fold` method `(s: S, t: T) => S` describing how to "fold" each element with the previous state
 - a `end(s: S)` method returning `M[U]` and finalizing the computation
 
Then a `FoldableM[M, F, T]` instance knows how stream elements of type `T` from a stream `F` through a `FoldM[M, T, U]` which will compute a final value `M[U]`. This is all very abstract so let's have a look at a simple example:
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
    
In the example above `count` is a `FoldM` where `M` is simply `Id`. Its internal state is an `Int` and is simply returned at the end of the fold. Then `List` is a Scalaz `Foldable` and there is a `FoldableM` instance available for any `Foldable` when `M` has a `Bind` instance (trivial here since `M = Id`).    
    
Besides those 2 traits the rest of the library offers:

 - combinators for `FoldM`s
 - type aliases for specialized folds, like `Fold[T, U]` when `M = Id`
 - standard `Fold[T, U]` folds for statistics or hashes
 - a way to "break" the iteration of values when a given state has been reached
 - a `FoldableM` instance for `Iterator[T]` to work with lines retrieved from `scala.io.Source`
 - a `FoldableM` instance for`Process[M, T]` when working with scalaz-stream
 - ways to create "side-effecting" folds in order to write files (`SinkM` folds)
  
### Combinators

The most useful combinator is `zip` (or `<*>`). It lets you "couple" 2 folds, run them both at once and get a pair of the results:
```scala
// already available in com.ambiata.origami.FoldId
// sum all elements using the Numeric[N].plus method
def plus[N : Numeric] = new Fold[N, N] {
  val num = implicitly[Numeric[N]]
  type S = N
  
  def start = num.zero
  def fold = (s: S, n: N) => num.plus(s, n)
  def end(s: S) = s
}

def countAndSum: Fold[Int, Int] = 
  count <*> plus
  
val list: List[Int] = List(1, 2, 3) 

// in one pass
countAndSum.run(list) == (3, 6) 
```
  
#### Other combinators
 
Here is a short-list of other useful combinators:
 
 - `<*` is like `<*>` (or `zip`) but ignores the end value of the second fold. This is useful when the other fold is only wanted for its side-effects (like writing to a file)
 
 - `map(f: U => V)` maps the end result `M[U]` to another value `M[V]`
 
 - `contramap` "adapts" the input elements of type `T` with a function `R => T` in order to build a `FoldM[M, R, U]` now accepting elements of type `R`  
 
 - `mapFlatten(f: U => M[V])` modifies the output `M[U]` of the fold into a `M[V]` value (when `M : Bind`)
 
 - `compose` feeds in all intermediary results of a given fold to another. For example a `scanl` fold for sums can be built by composing the `plus` fold (summing all elements) and the `list` fold (listing all elements). The resulting fold will return a list of all intermediate sums
 
 - `fromMonoid[M : Monoid]` creates a `Fold[M, M]` from a `Monoid`
 
 - `fromMonoidMap[T, M : Monoid](map: T => M)` creates a `Fold[T, M]` accepting elements of type `T` and using a `Monoid` to accumulate them
 
 - `fromFoldLeft[T, U](start: U)(fold: (U, T) => T)` creates a `Fold[T, U]` from a start value and a folding function
 
### Standard Folds

The `com.ambiata.origami.FoldId` object provides a few useful folds:

 - `count`, `countUnique`
 - `any(pred: T => Boolean)`, `all(pred: T => Boolean)` to check a predicate over elements of type `T`
 - `plus[N]`, `times[N]` when `N : Numeric`
 - `maximum[T]`, `minimum[T]` (where `T : Order`) but also `maximumBy`, `maximumOf` to compute the element having the maximum value of an attribute (the oldest person for example), or the maximum attribute value (the maximum age for example)
 - `mean`, `stddev`, `onlineStddev` (returns count + mean + standard deviation)
 - checksums: `md5` and `sha1`, with 2 variations. `md5` operates on `Array[Byte]` and `md5Bytes` accepts elements of type `Bytes = (Array[Byte], Int)` where the `Int` is the number of elements to read from the array (useful when working with `java.io.InputStream`s)
 
 
### Breakable FoldableM

It is sometimes useful to stop folding a structure when the state of a `FoldM` has reached a given point. A typical example is the `all` fold. When using `all` we know that we can stop checking elements as soon as one of them returns `false`. 

To accomodate this scenario there is a `foldMBreak` method on `FoldableM` and a corresponding `runBreak` method on `FoldM` which works when the state `S` is of the form `U \/ U` where a value of type `\/-(U)` signals that the folding can terminate:
```scala
import scalaz._, Scalaz._ // to get a Foldable instance for List

val list = List(true, true, false, true, true)

// will only iterate through the first 3 values
// note that it is necessary to use a val here otherwise type inference doesn't work
// because Scala can't decide if the type S of the fold is of type Boolean \/ Boolean
val allTrue = all[Boolean](v => v)
allTrue.runBreak(list) == false
 ```
(see [another example](#Breaking-out) further down for another example). 
    
### InputStream

Time to create some side effects! For example, folds can be used over a `java.lang.InputStream`, to read a file and compute a `SHA1` hash :
```scala
import com.ambiata.origami._, FoldId._, FoldableM._,
import com.ambiata.origami.effect._, FoldIO._
import java.io._

val fileInputStream = new FileInputStream(new File("file.txt"))       
val sha1: IO[String] = 
  bytesSha1.into[IO].run(fileInputStream) 
```
    
Let's break this code down. `bytesSha1` is a `Fold[Bytes, String]` which computes a `SHA1` when run through a stream of `Bytes`. However, since we are going to read a file we want this "folding" to happen inside the `IO` monad so we use `into` to transform `bytesSha1: FoldM[Id, Bytes, String]` into `FoldM[IO, Bytes, String]`. 

Then we can run this fold over an input stream because there is, in the `FoldableM` object, an instance of `FoldableM` for `InputStreams` (seen as streams producing `Bytes` elements).

### Source

Another common scenario is to read file lines with `scala.io.Source.fromFile(file).getLines` which returns an `Iterator[String]`. But there are different issues in using an `Iterator`.

#### State folding

Keeping track of state is not composable. Let's say I want to do something *and also* count the number of lines in the file. If I use vars my code gets scattered:
```scala    
val source = scala.io.Source.fromFile("file.txt")

// initialisation logic
var count = 0 

source.getLines.foreach { line =>
  // do something
   
  // folding logic
  count += 1
}
```    
The situation doesn't improve much by using `foldLeft` (but still better than variables):
```scala    
// the initial value is still separated from the "folding" method
source.getLines.foldLeft(0) { (count, line) =>
  // do something
       
  // folding logic
  count += 1
}
```  
And this gets worse if I need to keep track of more state (to check intermediary headers for example) or if I need to "finalize" the end result (to compute a hash). On the other hand a `Fold` is very composable:
```scala
// before
doSomething.run(source.getLines)
    
// after
(doSomething <*> count).run(source.getLines)
```
    
This way of doing also facilitates testing a lot because it becomes very easy to test folds in isolation from each other. You don't even need an `Iterator` to test `count` a `List` will do:
```scala
count.run(List(1, 2, 3)) == List(1, 2, 3).foldLeft(0)((n, _) => n + 1)
```

#### Breaking out    

Another issue with using an `Iterator` (or a `scalaz.Traversable` for that matter) is that there is no easy way to stop the iteration / traversal when necessary (people thinking "I could use an `Exception`", please don't!).

The ***origami*** solution for this feels a bit ad-hoc but at least provides something better than exceptions. Say you expect a file that is only 10 lines long and there's no use counting lines if that is not the case. You can use the `breakWhen` operator to define when to stop:
```scala
    count.breakWhen(n => n >= 10).runBreak((1 to 100).toList)
```

`breakWhen` transforms a normal `FoldM` where the state type variable is `S` to one where it is `S \/ S`. This new fold indicates with a "Right" value (`\/-`) that the iteration can stop. Otherwise state values are returned in a "Left" instance (`-\/`).

### SinkM

A special case of `FoldM` is `SinkM`. A `SinkM` is a fold which only has side effects, its type is `type SinkM[M, T] = FoldM[M, T, Unit]`. A `SinkM` can be created to output data to a file:
```scala
import com.ambiata.origami._ , FoldM._, FoldableM._, effect.FoldIO._

val sink: SinkM[IO, String] =
  fileUTF8LineSink("output.txt")

sink.contramap[Int](_.toString).run(List(1, 2, 3))
```

In the example above we create a `File` sink which will accept Strings and output them to a file. Note that we need to `contramap` the sink to transform each `Int` coming from the list to a `String`.

More generally sinks are generally used in conjonction with other folds to:

 1. output the streamed elements
 2. output the successive states
 3. output the final values

#### Output streamed elements

The `observe` method (or `<*`) can be used to output the input elements of a given fold:
```scala
val countElements: Fold[String, Int] =
  count[String]

// the countElements fold needs to be
// put in IO in order to be observed by a file sink
val fold: Fold[IO, String, Int] =
  (countElements.into[IO] <* fileUTF8LineSink("output.txt"))

// will output 3 lines in the output.txt file: "a", "b", "c"
fold.run(List("a", "b", "c")).unsafePerformIO == 3
```

#### Output state values

The `observeState` method (or `<<*`) can be used to output the successive states of a given fold:
```scala
val fold: Fold[IO, String, Int] =
  (countElements.into[IO] <<* fileUTF8LineSink("output.txt").contramap[Int](_.toString))

// will output 3 lines in the output.txt file: "0", "1", "2"
fold.run(List("a", "b", "c")).unsafePerformIO == 3
```

Since the "state" we observe here is of type `Int` we need to `contramap` the file sink in order to print Strings.

As you can see the "states" we observe here are all the state values *before* the call to the `fold` method. If you want to observe the states *after* the `fold` method you need to use `observeNextState` (or `<<<*`):
```scala
val fold: Fold[IO, String, Int] =
  (countElements.into[IO] <<<* fileUTF8LineSink("output.txt").contramap[Int](_.toString))

// will output 3 lines in the output.txt file: "1", "2", "3"
fold.run(List("a", "b", "c")).unsafePerformIO == 3
```

#### Output the last value

The last value `u: U` computed by a `FoldM[M, T, U]` is of type `M[U]`. If you want to output the last value you can simply rely on the `M` monad and the `mapFlatten` method

```scala
val fold: Fold[IO, String, Int] =
  (countElements.into[IO] <* fileUTF8LineSink("output.txt")).mapFlatten { last: Int =>
    fileUTF8LineSink("count.txt").contramap[Int](_.toString).run1(last)
  }
  
// will output 3 lines in the output.txt file: "a", "b", "c"
// and one line in the count.txt file: "3"
fold.run(List("a", "b", "c")).unsafePerformIO == 3
```
