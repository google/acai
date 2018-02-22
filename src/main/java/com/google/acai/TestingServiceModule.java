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

import com.google.common.testing.TearDown;
import com.google.common.testing.TearDownAccepter;
import com.google.common.testing.TearDownStack;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import javax.inject.Singleton;

/**
 * Abstract module which can be used to bind instances of {@link TestingService}.
 *
 * <p>Example usage:
 *
 * <pre>
 *   class MyModule extends AbstractModule {
 *     {@literal @}Override protected void configure() {
 *       install(new TestingServiceModule() {
 *         {@literal @}Override protected void configureTestingServices() {
 *           bindTestingService(MyTestingService.class);
 *         }
 *       }
 *     }
 *   }
 * </pre>
 */
public abstract class TestingServiceModule extends AbstractModule {

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

  /**
   * Testing service which enables support for Guava's {@link TearDownAccepter}.
   */
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

      @AfterTest
      void tearDown() {
        tearDowns.runTearDown();
      }
    }
  }
}
