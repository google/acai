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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestingServiceModuleTest {

  @Test
  public void servicesAreBound() {
    final ServiceToBindByInstance serviceInstance = new ServiceToBindByInstance();
    Injector injector = Guice.createInjector(TestingServiceModule.forServices(serviceInstance));

    Set<TestingService> boundServices =
        injector.getInstance(new Key<Set<TestingService>>(AcaiInternal.class) {});

    assertThat(boundServices).contains(serviceInstance);
  }

  @Test
  public void servicesAreBoundUsingFactoryMethod() {
    Injector injector =
        Guice.createInjector(
            TestingServiceModule.forServices(
                ServiceToBindByClass.class, ServiceToBindByClassTwo.class));

    Set<TestingService> boundServices =
        injector.getInstance(new Key<Set<TestingService>>(AcaiInternal.class) {});

    assertThat(boundServices.stream().map(Object::getClass))
        .containsExactly(ServiceToBindByClass.class, ServiceToBindByClassTwo.class);
  }

  private static class ServiceToBindByClass implements TestingService {}

  private static class ServiceToBindByClassTwo implements TestingService {}

  private static class ServiceToBindByInstance implements TestingService {}
}
