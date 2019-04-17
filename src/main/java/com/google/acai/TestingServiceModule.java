/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.acai;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.TearDown;
import com.google.common.testing.TearDownAccepter;
import com.google.common.testing.TearDownStack;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import javax.annotation.CheckReturnValue;
import javax.inject.Singleton;
import org.junit.After;

/**
 * Abstract module which can be used to bind instances of {@link TestingService}.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MyModule extends AbstractModule {
 *     {@literal @}Override protected void configure() {
 *       install(TestingServiceModule.forServices(MyTestingService.class);
 *     }
 *   }
 * </pre>
 */
public abstract class TestingServiceModule extends AbstractModule {

  /** Returns a new module which will configure bindings for all the specified {@code services}. */
  public static TestingServiceModule forServices(
      Iterable<Class<? extends TestingService>> services) {
    return new TestingServiceModule() {
      @Override
      protected void configureTestingServices() {
        for (Class<? extends TestingService> service : services) {
          bindTestingService(service);
        }
      }
    };
  }

  /** Returns a new module which will configure bindings for all the specified {@code services}. */
  @SafeVarargs
  @CheckReturnValue
  public static TestingServiceModule forServices(Class<? extends TestingService>... services) {
    return forServices(ImmutableList.copyOf(services));
  }

  @Override
  protected final void configure() {
    configureTestingServices();
  }

  protected final void bindTestingService(TestingService testingService) {
    Multibinder.newSetBinder(this.binder(), TestingService.class, AcaiInternal.class)
        .addBinding()
        .toInstance(testingService);
  }

  protected final void bindTestingService(Class<? extends TestingService> testingService) {
    Multibinder.newSetBinder(this.binder(), TestingService.class, AcaiInternal.class)
        .addBinding()
        .to(testingService);
  }

  /**
   * Implementing classes should override this method and call {@link
   * #bindTestingService(TestingService)} from within it.
   */
  protected abstract void configureTestingServices();

  /** Testing service which enables support for Guava's {@link TearDownAccepter}. */
  static class TearDownAccepterModule extends TestingServiceModule {
    @Override
    protected void configureTestingServices() {
      bind(TearDownService.class).in(Singleton.class);
      bindTestingService(TearDownService.class);
      bind(TearDownAccepter.class).to(TearDownService.class);
    }

    private static class TearDownService implements TestingService, TearDownAccepter {
      private final TearDownStack tearDowns = new TearDownStack();

      @Override
      public void addTearDown(TearDown tearDown) {
        tearDowns.addTearDown(tearDown);
      }

      @After
      void tearDown() {
        tearDowns.runTearDown();
      }
    }
  }
}
