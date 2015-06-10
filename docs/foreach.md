## Foreach school

Reading file lines with `scala.io.Source.fromFile(file).getLines` is very convenient. `getLines` returns an `Iterator[String]` which you can use with regular Scala collection operator (`map`, `filter`,...) for processing.

But there are different issues with the use of `Iterator` and `Source`.

#### State folding

Keeping track of state is not composable. Let's say I want to compute the total line size *and also* count the number of lines in the file. If I use vars my code gets scattered:
```scala
val source = scala.io.Source.fromFile("file.txt")

// initialisation logic
var count = 0
var totalSize = 0

source.getLines.foreach { line =>
  // folding logic
  totalSize += line.size
  count += 1
}
```
The situation doesn't improve much by using `foldLeft` (but still better than variables):
```scala
val (count, totalSize) =
  // the initial value is still separated from the "folding" method
  source.getLines.foldLeft((0, 0)) { case ((c, ts), line) =>
    (c + 1, ts + line.size)
  }
```  
And this gets worse if I need to keep track of more state (to check intermediary headers for example) or if I need to "finalize" the end result (to compute a the average line size for example).

On the other hand a `Fold` is very composable because it provides all the required functionality - initialization, folding, finalization - at once:
```scala
// before: only count lines
count.run(source.getLines)

// after: add the totalSize
(count <*> totalSize).run(source.getLines)
```

This way of doing also facilitates testing a lot because it becomes very easy to test folds in isolation from each other. You don't even need an `Iterator` to test `count`, a `List` will do:
```scala
count.run(List(1, 2, 3)) == List(1, 2, 3).foldLeft(0)((n, _) => n + 1)
```

#### Breaking out

Another issue with using an `Iterator` (or a `scalaz.Traversable` for that matter) is that there is no easy way to stop the iteration / traversal when necessary (people thinking "I could use an `Exception`", please don't!).

The ***origami*** solution for this feels a bit ad-hoc but at least provides something better than exceptions. It is explained on [this page](breakable.md)

#### Resource management

The last issue with using `Scala.io.Source` is resource safety. When you start iterating on a `BufferedSource` you need to remember about closing it even when there are exceptions.

Something like that:
```scala
val source = scala.io.Source.fromFile("file.txt")

try {
  source.getLines.foreach { line =>
    // folding logic
  }
} finally source.close
```
