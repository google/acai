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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

/**
 * Module which provides some limited compatibility with Guiceberry.
 *
 * <p>Allows some modules which were designed for Guiceberry to be reused with Acai. Currently
 * supports Guiceberry's TestScoped annotation and GuiceBerryEnvMain.
 */
class GuiceberryCompatibilityModule extends AbstractModule {
  private static final String GUICEBRRY_TEST_SCOPED_ANNOTATION = "com.google.guiceberry.TestScoped";
  private static final String GUICEBRRY_ENV_MAIN = "com.google.guiceberry.GuiceBerryEnvMain";
  private static final String ENV_MAIN_RUN_METHOD = "run";

  @Override
  protected void configure() {
    try {
      bindScope(
          Class.forName(GUICEBRRY_TEST_SCOPED_ANNOTATION).asSubclass(Annotation.class),
          TestScope.INSTANCE);
    } catch (ClassNotFoundException | ClassCastException e) {
      // TestScoped not on classpath, compatibility not required.
    }

    install(
        new TestingServiceModule() {
          @Override
          protected void configureTestingServices() {
            bindTestingService(GuiceBerryEnvMainService.class);
          }
        });
  }

  /** Service which runs any configured GuiceBerryEnvMain before all tests. */
  private static class GuiceBerryEnvMainService implements TestingService {
    @Inject Injector injector;

    @BeforeSuite
    public void run() throws Throwable {
      Class<?> envMainClass;
      try {
        envMainClass = Class.forName(GUICEBRRY_ENV_MAIN);
      } catch (ClassNotFoundException e) {
        // GuiceBerryEnvMain not on classpath, nothing to do.
        return;
      }
      if (injector.getExistingBinding(Key.get(envMainClass)) == null) {
        // No binding configured for GuiceBerryEnvMain, nothing to do.
        return;
      }
      Object envMain = injector.getInstance(envMainClass);
      try {
        envMainClass.getMethod(ENV_MAIN_RUN_METHOD).invoke(envMain);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException("Failed to invoke run on GuiceBerryEnvMain", e);
      }
    }
  }
}
