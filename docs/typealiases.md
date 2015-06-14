## Type aliases

Scala type inference can be a bit tricky, especially in presence of higher-kinded types. One way around that is to use the excellent [kind-projector compiler plugin](https://github.com/non/kind-projector). When necessary you can annotate methods with a type like `SafeT[M, ?]` instead of `({type l[A]=SafeT[M,A]})#l`.

Another way is to use type synonyms for common situations:

#### In the `com.ambiata.origami` package

 Type                  | Equivalent                       | Source      | Comment
 ----------------------| -------------------------------- | ----------- | ---  
 `Fold[T, U]`          | `FoldM[Id, T, U]`                | `FoldM`     | pure Fold
 `FoldState[T, U]`     | `FoldM[Id, T, U] { type S = U }` | `FoldM`     | pure Fold where the state and return value types are the same
 `SinkM[M[_], T]`      | `FoldM[M, T, Unit]`              | `FoldM`     | Fold which doesn't return any value
 `SinkM[M[_], T]`      | `FoldM[M, T, Unit]`              | `FoldM`     | Fold which doesn't return any value


#### In the `com.ambiata.origami.effect` package

 Type                  | Equivalent                       | Source         | Comment
 ----------------------| -------------------------------- | ---------------| ---  
 `SafeTIO[A]`          | `SafeT[IO, A]`                   | `SafeT`        | safe IO resources
 `SafeTTask[A]`        | `SafeT[Task, A]`                 | `SafeT`        | safe Task resources
 `FoldIO[T, U]`        | `FoldM[IO, T, U]`                | `FoldIO`       |
 `FoldSafeTIO[T, U]`   | `FoldM[SafeTIO, T, U]`           | `FoldIO`       |
 `Sink[T]`             | `FoldM[SafeTIO, T, Unit]`        | `FoldIO`       | the Sink type for IO is safe
 `FoldTask[T, U]`      | `FoldM[Task, T, U]`              | `FoldTask`     |
 `FoldSafeTTask[T, U]` | `FoldM[SafeTTask, T, U]`         | `FoldTask`     |
 `Sink[T, U]`          | `FoldM[SafeTTask, T, Unit]`      | `FoldTask`     | the Sink type for Task is safe


#### In the `com.ambiata.origami.stream` package

 Type                  | Equivalent                       | Source         | Comment
 ----------------------| -------------------------------- | ---------------| ---  
 `ProcessTask[T]`      | `Process[Task, T]`               | `FoldProcessM` |
