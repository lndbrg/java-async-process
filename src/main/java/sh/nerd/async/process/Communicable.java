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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An interface for representing classes that can communicate with stdin, out, err
 *
 * @param <T> the type to return when methods are called.
 */
interface Communicable<T> {

  /**
   * Sets the supplier that feeds data in to the process.
   *
   * The supplier is expected to produce lines without line endings, each supplied line is
   * fed into the buffer of the process, a platform specific new line is added and the buffer
   * is then flushed.
   *
   * @param supplier the supplier of the lines.
   * @return an instance of type T
   */
  T in(final Supplier<String> supplier);

  /**
   * Sets the consumer to feed data to when standard out has data.
   *
   * @param consumer called for each line in output
   * @return an instance of type T
   */
  T out(final Consumer<String> consumer);

  /**
   * Sets the consumer to feed data to when standard error has data.
   *
   * @param consumer called for each line in output
   * @return an instance of type T
   */
  T err(final Consumer<String> consumer);
}