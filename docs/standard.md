## Pure Folds

The `com.ambiata.origami.FoldId` object provides a few useful folds:

 name                                              | comment
 ------------------------------------------------- | -------
 `count`, `countLong`                              | count elements (return `Int` or `Long`)
 `countOf(predicate)`                              | count the elements satisfying a given predicate (`countLongOf` for `Long`)
 `countUnique`                                     | count unique elements (uses a `HashSet`)
 `any(predicate)`                                  | check if any element satisfies a predicate
 `all(predicate)`                                  | check if all the elements satisfy a predicate
 `plus[N]`, `times[N]`                             | sum or multiply elements (`N : Numeric`)
 `maximum[T]`, `minimum[T]`                        | maximum or minimum element (`T : Order`)
 `maximumBy`, `minimumBy`                          | maximum or minimum element when comparing on a given attribute (the oldest person for example)
 `maximumOf`, `minimumOf`                          | maximum or minimum attribute of streamed elements (the maximum age for example)
 `first`, `last`                                   | first or last streamed element
 `firstN(n)`, `lastN(n)`                           | first or last `n` elements
 `flips`, `flipsLong`                              | number of times the streamed elements are changing their value
 `proportion(predicate)`                           | proportion of elements satisfying a predicate
 `list`                                            | accumulate all elements in a list
 `gradient: Fold[(T,V), Double]`                   | gradient of a given variable `T`, compared to another one `V`
 `mean`                                            | average of streamed elements
 `stddev`                                          | standard deviation
 `onlineStddev`                                    | triplet of (count, mean, standard deviation) computed in one pass
 `randomInt`, `randomLong`, `randomDouble`         | fold where the state is a new random `Int`, `Long`, `Double`
 `reservoirSampling`                               | return one element from the stream where each streamed element has the same probability to be chosen

### Checksums

Checksums come in 2 flavors, as folds of `Array[Byte]` or folds of `Bytes = (Array[Byte], Int)`. The second type where the `Int` is the number of elements to read from the array is useful when working with `java.io.InputStream`s:

 name                                              | comment
 ------------------------------------------------- | -------
`md5`, `md5Bytes`                                  | compute a MD5 hash
`sha1`, `sha1Bytes`                                | compute a SHA1 hash
`checksum(algo)`, `checksumBytes(algo)`            | compute a checksum for a given algorithm where `algo` is a string recognized by the `java.security.MessageDigest` class
