object t {
import com.ambiata.origami._, Origami._
import effect.SafeT
import scalaz.effect.IO
import scala.io._

def readLines(file: BufferedSource): IO[Iterator[String]] = ???

val source = scala.io.Source.fromFile("test.txt")

val read: SafeT[IO, Iterator[String]] =
  readLines(source) `finally` IO(source.close)
}
