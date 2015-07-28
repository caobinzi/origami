# <center><img src="http://upload.wikimedia.org/wikipedia/commons/f/fd/Origami-crane.jpg" alt="fold" width="150px"/> Origami </center>
<br/>
[![Build Status](https://travis-ci.org/ambiata/origami.png)](https://travis-ci.org/ambiata/origami)

The ***origami*** project provides "Monadic folds" to process streams of data in a composable fashion.

With monadic folds you can:

 - accumulate state, for example count the number of elements or compute a hash value

 - output results to a file or a database as you compute them

 - display the end value on the console

Moreover folds are composable:

 - you can implement and test folds independently from the data stream they will be used on

 - you can run two folds at the same time without having to make two passes over the data stream

 - you can add side-effects like writing results to a file later on if you need to

Finally, side-effecting folds like `Sinks` are operating inside a `SafeT` monad to make sure that resources are always released, even when there are exceptions.

Read the [documentation](http://origami.readthedocs.org/en/latest)  for more information.

### Installation

Add the following to your `build.sbt` file:
```scala
resolvers += Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns)

libraryDependencies += "com.ambiata" %% "origami" % "1.0"
```
