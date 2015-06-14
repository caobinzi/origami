## Implicits

Most implicits are accessible by importing the `com.ambiata.origami.Origami` object. However implicits resolution might not always work as expected, especially when you want to write:
```
myFold.into[M].run(myStream)
```

Where `M` is a given monad or even a `SafeT[M]`. You might then want to import the right implicits selectively:

### Natural transformations

An implicit natural transformation `N ~> M` is necessary when invoking the `into[N]` function on a `FoldM[M, T, U]`:

 From                  | To                      | Source              | Comment
 ----------------------| ----------------------- | ------------------- | ---  
 `List`                | `Iterator`              | `FoldM`             |
 `Id`                  | `IO`                    | `effect.FoldIO`     |
 `Id`                  | `Task`                  | `effect.FoldTask`   |
 `Id`                  | `SafeTIO`               | `effect.FoldSafeT`  |
 `Id`                  | `SafeTTask`             | `effect.FoldSafeT`  |
 `Task`                | `SafeTTask`             | `effect.FoldSafeT`  |


### FoldableM

Then, if you want to use the `run` method on a `FoldM[M, T, U]` you need a `FoldableM[M, S, T]` implicit where `S` is the type of the stream and `T` the type of streamed elements:

 From                  | To                                     | Source                      | Comment
 ----------------------| ---------------------------------------| --------------------------- | -------
 `Iterator[T]`         | `Foldable[M, Iterator[T], T]`          | `FoldableM`                 |
 `Foldable[T]`         | `Foldable[M, Foldable[T], T]`          | `FoldableM`                 |
 `BufferedSource`      | `Foldable[M, BufferedSource, String]`  | `FoldableM`                 |
 `InputStream`         | `Foldable[M, InputStream, Bytes]`      | `FoldableM`                 | [[1]](/implicits/#1/)
 `InputStream`         | `Foldable[M, InputStream, String]`     | `FoldableM`                 | [[1]](/implicits/#1/)
 `Process[M, T]`       | `Foldable[M, Process[M, T], T]`        | `stream.FoldableProcessM`   |
 `Process[M, T]`       | `Foldable[N, Process[M, T], T]`        | `stream.FoldableProcessM`   | if there is an implicit `N ~> M`

<div id="1">[1]: the inference depends on the type of elements that the `FoldM` is accepting, `Bytes` or `String`</div>
