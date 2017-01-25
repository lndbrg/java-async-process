/*
 * -\-\-
 * Async Process
 * --
 * Copyright (C) 2017 Olle Lundberg
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package sh.nerd.async.process;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Class that creates an async process
 */
public class AsyncProcess {

  private final String[] command;
  private final Optional<Supplier<String>> inSupplier;
  private final Optional<Consumer<String>> outConsumer;
  private final Optional<Consumer<String>> errConsumer;
  private final Function<Runnable, CompletionStage<Void>> runner;

  /**
   * C'tor
   *
   * @param cmd command to run
   * @param in  supplier fro std in
   * @param out consumer for std out
   * @param err consumer for std err
   * @param exe the executor to run futures on
   */
  AsyncProcess(final String[] cmd,
               final Supplier<String> in,
               final Consumer<String> out,
               final Consumer<String> err,
               final Executor exe) {

    command = cmd;
    inSupplier = Optional.ofNullable(in);
    outConsumer = Optional.ofNullable(out);
    errConsumer = Optional.ofNullable(err);
    runner = isNull(exe) ? CompletableFuture::runAsync : runnable -> runAsync(runnable, exe);

  }

  /**
   * Starts the process
   */
  public Result start() throws IOException {
    final Process exec = Runtime.getRuntime().exec(command);
    final Function<Supplier<String>, CompletionStage<Void>> in = redirect(exec.getOutputStream());
    final Function<Consumer<String>, CompletionStage<Void>> out = redirect(exec.getInputStream());
    final Function<Consumer<String>, CompletionStage<Void>> err = redirect(exec.getErrorStream());
    final Result result = Result.of(exec, in, out, err, runner);
    inSupplier.ifPresent(result::in);
    outConsumer.ifPresent(result::out);
    errConsumer.ifPresent(result::err);
    return result;
  }

  private Function<Supplier<String>, CompletionStage<Void>> redirect(final OutputStream stream) {
    return supplier -> {
      final CompletableFuture<Void> future = new CompletableFuture<>();
      runner.apply(
          () -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream))) {
              try {
                produce(supplier, writer);
                future.complete(null);
              } catch (UncheckedIOException e) {
                // TODO: this is wrong, we need to expose a way for the user to singal end of
                // stdin. THe exception handling here is also broken. Needs tests and we should
                // also make sure to join the returned completablefuture that is return from this
                // fuction.
                // Maybe supplier is not the way to go. Stream.generate creates an infinite stream,
                // which is not really what we want. Maybe we should just expose an async writer.
              } finally {
                future.complete(null);
              }
            } catch (IOException e) {
              future.completeExceptionally(e);
            }
          }
      );
      return future;
    };
  }

  private void produce(final Supplier<String> supplier, final BufferedWriter writer) {
    /*
    If we continue to expose a supplier, we probably want the user to block in their supplier
    until they have data to give us and send us a null when they want us to stop.
     */
    Stream.generate(supplier)
        .filter(Objects::nonNull)
        .forEachOrdered(string -> {
          try {
            writer.write(string);
            writer.newLine();
            writer.flush();
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }


  /**
   * Prepares to redirect an input stream to a consumer
   *
   * @return a function that when applied will start to consume the input stream.
   */
  private Function<Consumer<String>, CompletionStage<Void>> redirect(final InputStream stream) {
    return consumer -> {
      final CompletableFuture<Void> future = new CompletableFuture<>();
      runner.apply(
          () -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
              reader.lines().forEachOrdered(consumer);
              future.complete(null);
            } catch (IOException e) {
              future.completeExceptionally(e);
            }
          }
      );
      return future;
    };
  }

  /**
   * Creates a new Builder that can be used to start an AsyncProcess
   */
  public static Builder cmd(final String... cmd) {
    return new Builder().cmd(cmd);
  }

  /**
   * Run a command
   */
  public static Result run(final String... cmd) throws IOException {
    return new Builder().cmd(cmd).start();
  }

  /**
   * Run a command
   */
  public static Result run(final Consumer<String> out, final String... cmd) throws IOException {
    return new Builder().cmd(cmd).out(out).start();
  }

  /**
   * Run a command
   */
  public static Result run(final Consumer<String> out,
                           final Consumer<String> err,
                           final String... cmd) throws IOException {
    return new Builder().cmd(cmd).out(out).err(err).start();
  }

  /**
   * Run a command
   */
  public static Result run(final Supplier<String> in,
                           final Consumer<String> out,
                           final Consumer<String> err,
                           final String... cmd) throws IOException {
    return new Builder().cmd(cmd).in(in).out(out).err(err).start();
  }


  /**
   * Builder for async process. Shouldn't be created by the user.
   * Rather use {@link AsyncProcess#cmd(String...)}
   */
  static class Builder implements Communicable<Builder> {

    private Supplier<String> inSupplier;
    private Consumer<String> outConsumer;
    private Consumer<String> errConsumer;
    private String[] command;
    private Executor executor;

    /**
     * {@inheritDoc}
     */
    @Override
    public Builder out(final Consumer<String> consumer) {
      outConsumer = requireNonNull(consumer);
      return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Builder err(final Consumer<String> consumer) {
      errConsumer = requireNonNull(consumer);
      return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Builder in(final Supplier<String> supplier) {
      inSupplier = requireNonNull(supplier);
      return this;
    }

    /**
     * Set the command to execute.
     *
     * @param cmd the command to execute
     */
    public Builder cmd(final String... cmd) {
      command = requireNonNull(cmd);
      return this;
    }

    /**
     * Set the executor to run on.
     *
     * @param exec executor to run on
     */
    public Builder executor(final Executor exec) {
      executor = requireNonNull(exec);
      return this;
    }


    /**
     * Kicks off the async process.
     *
     * @return a {@link Result} object.
     */
    public Result start() throws IOException {
      return new AsyncProcess(command, inSupplier, outConsumer, errConsumer, executor).start();
    }

  }


}
