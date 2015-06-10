## Breakable FoldableM

It is sometimes useful to stop folding a structure when the state of a `FoldM` has reached a given point. A typical example is the `all` fold. When using `all` we know that we can stop checking elements as soon as one of them returns `false`.

To accommodate this scenario there is a `foldMBreak` method on `FoldableM` and a corresponding `runBreak` method on `FoldM` which works when the state `S` is of the form `U \/ U`.
In that case, a value of type `\/-(U)` signals that the folding can terminate:
```scala
import scalaz._, Scalaz._ // to get a Foldable instance for List

val list = List(true, true, false, true, true)

// will only iterate through the first 3 values
// note that it is necessary to use a val here otherwise type inference doesn't work
// because Scala can't decide if the type S of the fold is of type Boolean \/ Boolean
val allTrue = all[Boolean](v => v)

allTrue.runBreak(list) == false
```

### Create breaks

You can use the `breakWhen` operator to define when to stop:
```scala
count.breakWhen(_ >= 10).runBreak((1 to 100).toList)
```

`breakWhen` transforms a normal `FoldM` where the state type variable is `S` to one where it is `S \/ S`. This new fold indicates with a "Right" value (`\/-`) that the iteration can stop. Otherwise state values are returned in a "Left" instance (`-\/`).
