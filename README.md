# foldm

Monadic folds

# WORK IN PROGRESS, NOT READY FOR GENERAL USE!!!

<img src="http://upload.wikimedia.org/wikipedia/commons/f/fd/Origami-crane.jpg" alt="fold" width="400px"/>

## Presentation

When processing data streams it is often necessary to cumulate different "effects":

 - accumulate some state (for example counting the number of elements, or computing a hash value)
 - output results to a file or a database
 - display the end value on the console (or all the successive values)
 
Moreover we want to be able to:

 - develop these features in a composable way. It should be possible to describe and test the counting of elements (or other piece of statistics like mean/variance) independently from its output to a file
 
 - do all sorts of computations and side-effects in **one** traversal (not have to read a file twice to output the number of lines and the md5)
 
### `FoldM` and `FoldableM`
 
The ***foldm*** library offers 2 abstractions to do this: `FoldM` and `FoldableM`. Basically a `FoldM[T, M, U]` instance has:

 - a `type S` representing the accumulated state
 - a `start` method returning an initial `M[S]` element
 - a `fold` method `(s: S, t: T) => S` describing how to "fold" each element with the previous state
 - a `end(s: S)` method returning `M[U]` and finalizing the computation
 
Then a `FoldableM[M, T]` instance simply knows how to take a `FoldM[T, M, U]` instance to get the final `M[U]` result. This is all a bit abstract so let's have a look at a very simple example:

    import com.ambiata.foldm._, FoldableM._    

    def count[T] = new FoldM[T, Id, Int] {
      type S = Int
      def start = 0
      def fold = (s: S, t: T) => s + 1
      def end(s: S) = s
    }
    
    val list: List[Int] = List(1, 2, 3)
    
    FoldableM[List, Id].foldm(list)(count) == 3
    
    // or simply
    count.run(list) == 3
    
In the example above `count` is a `FoldM` where `M` is simply `Id`. Its internal state is an `Int` and is simply returned at the end of the fold. Then `List` is a Scalaz `Foldable` and there is a `FoldableM` instance available for any `Foldable` when `M` has a `Bind` instance (trivial here since `M = Id`).    
    
Besides those 2 traits the rest of the library offers:

 - combinators for `FoldM`s
 - standard `Fold[T, U]` folds for doing statistics or hashes
 - a way to "break" the iteration of values when a given state has been reached
 - type aliases for specialized folds, like `Fold[T, U]` when `M = Id` and specialized `FoldableM`
 - a `FoldableM` instance for `Iterator[T]` to be able to work with lines retrieved from `scala.io.Source`
 - a `FoldableM` instance for`Process[M, T]` when working with scalaz-stream
 - ways to create "side-effecting" folds to write to files (`SinkM` folds)
  
### Combinators

The most useful combinator is `zip` (or `<*>`). It allows to "couple" 2 folds, run them both at once and get a pair of the results:

    // already available it com.ambiata.foldm.FoldId.scala
    def plus[N : Numeric] = new FoldM[N, Id, N] {
      val num = implicitly[Numeric[N]]
      type S = N
      
      def start = num.zero
      def fold = (s: S, n: N) => num.plus(s, n)
      def end(s: S) = s
    }
    
    def countAndSum: FoldM[Int, Id, Int] = 
      count <*> plus
      
    val list: List[Int] = List(1, 2, 3) 
    
    // in one pass
    countAndSum.run(list) == (3, 6) 
  
#### Other combinators
 
Here is a short-list of other useful combinators:
 
 - `<*` is like `<*>` (or `zip`) but ignores the end value of the second fold. This is useful when the other fold is only wanted for its side-effects (like writing to a file)
 - `map` maps the end result `M[U]` to another value `M[V]` (so that `FoldM` can be made a `Functor` if `M` is a `Functor`)
 - `contramap` "adapts" the input elements of type `T` with a function `R => T` in order to build a `FoldM[R, M, U]` now accepting elements of type `R`  
 - `mapFlatten` uses a function `U => M[V]` to modify the output `M[U]` of the fold into a `M[V]` value
 - `compose` feeds in all intermediary results of a given fold to another. For example a `scanl` fold for sums can be built by composing the `plus` fold (summing all elements) and the `list` fold (listing all elements). The resulting fold will return a list of all intermediate sums
 
 - `fromMonoid[M : Monoid]` creates a `Fold[M, M]` from a `Monoid`
 - `fromMonoidMap[T, M : Monoid](map: T => M)` creates a `Fold[T, M]` accepting elements of type `T` and using a `Monoid` to accumulate them
 - `fromFoldLeft[T, U](start: U)(fold: (U, T) => T)` creates a `Fold[T, U]` from a start value and a folding function
 
### Standard Folds

The `com.ambiata.foldm.FoldId` object provides a few useful folds:

 - `count`, `countUnique`
 - `any(pred: T => Boolean)`, `all(pred: T => Boolean)` to check a predicate over elements of type `T`
 - `plus[N]`, `times[N]` when `N` is `Numeric`
 - `maximum[T]`, `minimum[T]` where `T` has an `Order` instance but also `maximumBy`, `maximumOf` to compute the element having a maximum attribute (the oldest person for example), or the maximum attribute (the maximum age for example)
 - `mean`, `stddev`, `onlineStddev` (returns count + mean + standard deviation)
 - checksums: `md5` and `sha1`, with 2 variations. `md5` operates on `Array[Byte]` and `md5Bytes` accepts elements of type `Bytes = (Array[Byte], Int)` where the `Int` is the number of elements to read from the array
 
 
### Breakable FoldableM

It is sometimes useful to stop folding a structure when the state of a `FoldM` has reached a given point. The "classical" example is the use of the `all` or `any` folds. When using `all` we know that we can stop checking elements as soon as one of them returns `false`. 

To accomodate this scenario there is a `foldMBreak` method on `FoldableM` and a corresponding `runBreak` method on `FoldM` which works when the state `S` is of the form `U \/ U` where a value of type `-\/(U)` signals that the folding can terminate:

    import scalaz._, Scalaz._ // to get a Foldable instance for List

    val list = List(true, true, false, true, true)
    
    // will only iterate through the first 3 values
    // note that it is necessary to use a val here otherwise type inference doesn't work
    // because Scala can't decide if the type S of the fold is of type Boolean \/ Boolean
    val allTrue = all[Boolean](v => v)
    allTrue.runBreak(list) == false
    
### InputStream

Time to create some side effects!    
    


  