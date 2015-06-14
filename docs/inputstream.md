## InputStream

Folds can also be used over a `java.lang.InputStream`, for example to read a file and compute a `SHA1` hash:
```scala
import com.ambiata.origami._, Origami._
import scalaz._, Scalaz._
import scalaz.effect._
import java.io._

val fileInputStream = new FileInputStream(new File("file.txt"))

val sha1: IO[String] =
  sha1Bytes.into[IO].run(fileInputStream)
```

Let's break this code down.

`sha1Bytes` is a `Fold[Bytes, String]` which computes a `SHA1` when run through a stream of `Bytes`. However, since we are going to read a file we want this "folding" to happen inside the `IO` monad so we use `into` to transform `sha1Bytes: FoldM[Id, Bytes, String]` into `FoldM[IO, Bytes, String]`.

Then we can run this fold over an `InputStream` because there is, in the `FoldableM` object, an instance of `FoldableM` for `InputStreams` (seen as streams producing `Bytes` elements).

We are also not limited to `InputStreams` being seen as a source of `Bytes` because there is also a `FoldableM` instance to interpret them as `UTF-8` lines:
```scala
val fileInputStream = new FileInputStream(new File("file.txt"))

val numberOfLines: IO[Int] =
  count[String].into[IO].run(fileInputStream)
```

### Resources

By default running the fold will read the input stream but will ***not close it***. In particular if any exception is thrown it is the responsibility of the client to catch the exception and close the input stream.

You can avoid this by using the `SafeT[M, ?]` monad transformer. The code looks almost the same, but uses `into[SafeTIO]`:

```scala
import com.ambiata.origami._, Origami._
import scalaz._, Scalaz._
import scalaz.effect._
import java.io._

val fileInputStream = new FileInputStream(new File("file.txt"))

val sha1: SafeTIO[String] =
  sha1Bytes.into[SafeTIO].run(fileInputStream)
```

See [SafeT](safet.md) for a more detailed description of the `SafeT` monad transformer.
