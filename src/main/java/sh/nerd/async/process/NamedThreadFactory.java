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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread factory that creates threads with a prefix.
 */
public class NamedThreadFactory implements ThreadFactory {

  private final AtomicInteger count = new AtomicInteger(0);
  private final ThreadFactory defaultThreadFactory;
  private final String prefix;

  /**
   * Creates a {@link ThreadFactory} that spawns threads named after the parameter prefix
   *
   * @param prefix prefix to use
   */
  public static NamedThreadFactory withPrefix(final String prefix) {
    return new NamedThreadFactory(requireNonNull(prefix));
  }

  NamedThreadFactory(final String threadPrefix) {
    prefix = threadPrefix + "-";
    defaultThreadFactory = Executors.defaultThreadFactory();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Thread newThread(final Runnable runnable) {
    final Thread thread = defaultThreadFactory.newThread(runnable);
    thread.setName(prefix + count.getAndIncrement());
    return thread;
  }
}
