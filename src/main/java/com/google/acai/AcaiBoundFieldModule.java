/*
 * Copyright 2021 Google Inc. All rights reserved.
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.spi.Message;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.inject.testing.fieldbinder.BoundFieldModule.BoundFieldInfo;
import java.util.function.Function;

/**
 * Wrapper around {@link BoundFieldModule}. Binds all fields annotated with {@code Bind(lazy=true)}
 * of the given class to a {@code TestScoped} instance, so the values are reset to default for every
 * test.
 *
 * <p>To use the lazy binding is important to either {@code @Inject} a {@code Provider} in your
 * class under test, or inject a {@code Provider} of the class in your test as shown below.
 *
 * <p>Usage:
 *
 * <pre><code>
 * &#64;Rule public final Acai acai = new Acai(TestEnv.class);
 *
 * public static class TestEnv extends AbstractModule {
 *   protected void configure() { install(AcaiBoundFieldModule.of(Vars.class); }
 * }
 *
 * public static class Vars {
 *   &#64;Bind(lazy = true) &#64;MyAnnotation String value = "default";
 * }
 *
 * &#64;Inject Vars vars;
 * &#64;Inject Provider&lt;ClassUnderTest&rt; instance; // injects &#64;MyAnnotation String
 *
 * &#64;Test public void test1() {
 *   vars.value = "test";
 *
 *   instance.get().foo(); // uses "test"
 * }
 *
 * &#64;Test public void test2() {
 *   instance.get().foo(); // uses "default"
 * }
 * </code></pre>
 */
public final class AcaiBoundFieldModule<T> extends AbstractModule {
  private final Class<T> clazz;
  private final T instance;

  private AcaiBoundFieldModule(Class<T> clazz) {
    this.clazz = clazz;

    try {
      instance = clazz.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Use like: {@code install(AcaiBoundFieldModule.of(Vars.class));} in your TestEnv.
   *
   * <p>NOTE: The class passed must be {@code public static}.
   */
  public static <T> AcaiBoundFieldModule<T> of(Class<T> clazz) {
    return new AcaiBoundFieldModule<>(clazz);
  }

  @Override
  protected void configure() {
    BoundFieldModule module = BoundFieldModule.of(instance);
    ImmutableMap<BoundFieldInfo, Object> defaultValues =
        module.getBoundFields().stream()
            .filter(
                info -> {
                  if (!info.getBindAnnotation().lazy()) {
                    binder()
                        .addError(
                            new Message(
                                info.getField(), "All bindings must use @Bind(lazy = true)"));
                    return false;
                  } else {
                    return true;
                  }
                })
            .collect(toImmutableMap(Function.identity(), BoundFieldInfo::getValue));

    TestingService testingService =
        new TestingService() {
          @BeforeTest
          void resetInstance() {
            defaultValues.forEach(
                (info, value) -> {
                  try {
                    info.getField().set(instance, value);
                  } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                  }
                });
          }
        };

    bind(clazz).toInstance(instance);
    install(module);

    install(
        new TestingServiceModule() {
          @Override
          protected void configureTestingServices() {
            bindTestingService(testingService);
          }
        });
  }
}
