Async Process
=============
[![Maven Central](https://img.shields.io/maven-central/v/sh.nerd/async-process.svg?style=flat-square)]()
[![Travis](https://img.shields.io/travis/lndbrg/java-async-process.svg?style=flat-square)]()
[![Codecov](https://img.shields.io/codecov/c/github/lndbrg/java-async-process.svg?style=flat-square)]()
[![codebeat badge](https://codebeat.co/badges/5518c2ce-1c46-474d-94e6-8c5808258d4f)](https://codebeat.co/projects/github-com-lndbrg-java-async-process-master)

Small library for making it easier to spawn processes in java with a modern api.

Usage
-----
AysncProcess comes with a few static methods, to either create a builder that can be launched by
calling `start()`.

Prepare a command and then spawn it:
```java
AsyncProcess.cmd("echo", "foo").start();
```

Or calling the `run()` method to directly spawn a command
```java
AsyncProcess.run("echo", "foo");
```

The run method is overloaded to make it possible to directly spawn commands with stdin/out/err set,
it is recommended to use the builder created by the cmd builder in order to get clearer code though.

### Advanced usage

Here the cmd method is imported statically:
```java
    cmd("echo", "foo")
        .in(supplier)
        .out(consumer)
        .err(consumer)
        .start();
```

In sets the stdin supplier, expected to be of type `Supplier<String>` it expected to block when it
doesn't have data and the supplier will never be called again after supplying a null value.

### In `pom.xml`

```xml
<dependency>
  <groupId>sh.nerd</groupId>
  <artifactId>async-process</artifactId>
  <version>$VERSION</version>
</dependency>
```


Todo
----
- [x] Give the user the possibility to specify environment through a hashmap
- [ ] Go through handling of the communication threads and verify the paths
- [ ] Better documentation
- [ ] Any other TODOs in the code.
