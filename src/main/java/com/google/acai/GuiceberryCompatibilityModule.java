/*
 * Copyright 2018 Google Inc. All rights reserved.
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

import com.google.auto.value.AutoValue;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

/**
 * Module which provides some limited compatibility with GuiceBerry.
 *
 * <p>Allows some modules which were designed for GuiceBerry to be reused with Acai. Currently
 * supports GuiceBerry's {@code TestScoped} annotation, {@code GuiceBerryEnvMain}, {@code
 * TestWrapper} and {@code TestScopeListener}.
 *
 * <p>Intended to facilitate reuse of code which cannot be changed and allow migrations to be done
 * incrementally. Not intended to provide a complete reimplementation of GuiceBerry. Using
 * GuiceBerry concepts in tests that use Acai is not recommended.
 */
class GuiceberryCompatibilityModule extends AbstractModule {
  private static final String GUICEBRRY_TEST_SCOPED_ANNOTATION = "com.google.guiceberry.TestScoped";
  private static final MethodReference GUICEBERRY_ENV_MAIN_RUN =
      MethodReference.create("com.google.guiceberry.GuiceBerryEnvMain", "run");
  private static final MethodReference TEST_WRAPPER_RUN_BEFORE_TEST =
      MethodReference.create("com.google.guiceberry.TestWrapper", "toRunBeforeTest");
  private static final MethodReference TEST_SCOPE_LISTENER_ENTERING_SCOPE =
      MethodReference.create(
          "com.google.inject.testing.guiceberry.TestScopeListener", "enteringScope");
  private static final MethodReference TEST_SCOPE_LISTENER_EXITING_SCOPE =
      MethodReference.create(
          "com.google.inject.testing.guiceberry.TestScopeListener", "exitingScope");

  @Override
  protected void configure() {
    classForName(GUICEBRRY_TEST_SCOPED_ANNOTATION)
        .map(clazz -> clazz.asSubclass(Annotation.class))
        .ifPresent(annotation -> bindScope(annotation, TestScope.INSTANCE));

    install(TestingServiceModule.forServices(GuiceBerryService.class));
  }

  /** Adapter which wraps GuiceBerry concepts in a TestingService. */
  private static class GuiceBerryService implements TestingService {
    @Inject Injector injector;

    @BeforeSuite
    public void run() throws Throwable {
      invokeIfBound(injector, GUICEBERRY_ENV_MAIN_RUN);
    }

    @BeforeTest
    public void beforeTest() throws Throwable {
      invokeIfBound(injector, TEST_SCOPE_LISTENER_ENTERING_SCOPE);
      invokeIfBound(injector, TEST_WRAPPER_RUN_BEFORE_TEST);
    }

    @AfterTest
    public void afterTest() throws Throwable {
      invokeIfBound(injector, TEST_SCOPE_LISTENER_EXITING_SCOPE);
    }
  }

  /**
   * Invokes the nullary {@code method} if there is an existing binding for its class on {@code
   * injector}.
   *
   * <p>If {@code className} cannot be found in the classpath or there is no binding configured this
   * method does nothing.
   */
  private static void invokeIfBound(Injector injector, MethodReference method) throws Throwable {
    Optional<Class<?>> clazz =
        classForName(method.className())
            .filter(c -> injector.getExistingBinding(Key.get(c)) != null);

    if (!clazz.isPresent()) {
      // No existing binding for class, nothing to do.
      return;
    }

    try {
      clazz.get().getMethod(method.methodName()).invoke(injector.getInstance(clazz.get()));
    } catch (InvocationTargetException e) {
      throw e.getCause();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to invoke run on GuiceBerryEnvMain", e);
    }
  }

  private static Optional<Class<?>> classForName(String className) {
    try {
      return Optional.of(Class.forName(className));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  @AutoValue
  abstract static class MethodReference {
    static MethodReference create(String className, String methodName) {
      return new AutoValue_GuiceberryCompatibilityModule_MethodReference(className, methodName);
    }

    abstract String className();

    abstract String methodName();
  }
}
