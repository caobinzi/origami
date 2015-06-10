## Combinators

The most useful combinator is `zip`, or `<*>`.

It lets you "couple" 2 folds, run them both at once and get a pair of results:
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

// "zip" the 2 folds
def countAndSum: Fold[Int, (Int, Int)] =
  count <*> plus

val list: List[Int] = List(1, 2, 3)

// run the 2 folds in one pass
countAndSum.run(list) == ((3, 6))

// and now we can compute
// the mean in one pass
val mean: Fold[Int, Double] = countAndSum.map { case (n, s) =>
  if (n == 0) 0.0
  else        s.toDouble / n
}

mean.run(list) == 2

```

### Other combinators

These combinators can be used to create folds:

 name                                              | comment
 ------------------------------------------------- | -------
 `fromMonoid[M : Monoid]`                          | create a `Fold[M, M]` from a `Monoid`
 `fromMonoidMap[T, M : Monoid](map: T => M)`       | create a `Fold[T, M]` accepting elements of type `T` and using a `Monoid` to accumulate them
 `fromFoldLeft[T, U](start: U)(fold: (U, T) => T)` | create a `Fold[T, U]` from a start value and a folding function


Those combinators are used on an existing fold of type `FoldM[M, T, U]`:

 name                                              | comment
 ------------------------------------------------- | -------
 `observe`, `<*`                                   | like `<*>` (or `zip`) but ignores the end value of the second fold. This is useful when the other fold is only wanted for its side-effects (like writing to a file)
 `observeState(sink)`, `<<*`                       | observe the successive states of a fold
 `observeNextState`, `<<<*`                        | observe the successive states of a fold, after applying the `fold` function
 `***(f: FoldM[M, V, W])`                          | "parallel composition", create a fold `FoldM[M, (T, V), (U, W)]`
 `first[V]`                                        | create a `Fold[M, (T, V), (U, V)]` where values of type `V` pass through
 `second[V]`                                       | create a `Fold[M, (V, T), (V, U)]` where values of type `V` pass through
 `map(f: U => V)`                                  | map the end result `M[U]` to another value `M[V]`
 `contramap(f: R => T)`                            | "adapt" the input elements of type `T` with a function `R => T` in order to build a `FoldM[M, R, U]` now accepting elements of type `R`  
 `mapFlatten(f: U => M[V])`                        | modify the result `M[U]` of the fold into a `M[V]` value
 `pipe(f: FoldM[M, U, V])`                         | run another fold on the end result
 `compose`                                         | feed in all intermediary results of a given fold to another. For example a `scanl` fold for sums can be built by composing the `plus` fold (summing all elements) and the `list` fold (listing all elements). The resulting fold will return a list of all intermediate sums
