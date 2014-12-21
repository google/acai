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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class DependenciesTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  private static class ServiceA implements TestingService {}
  private static class ServiceB implements TestingService {}

  @Test
  public void arbitraryOrdering() {
    Set<TestingService> testingServices =
        ImmutableSet.of(new ServiceA(), new ServiceB(), new ServiceA());

    assertThat(Dependencies.inOrder(testingServices)).has().exactlyAs(testingServices);
  }

  private static class Service1 implements TestingService {}
  @DependsOn(Service1.class) private static class Service2 implements TestingService {}
  @DependsOn(Service2.class) private static class Service3 implements TestingService {}

  @Test
  public void linearOrder() {
    ImmutableList<TestingService> testingServices =
        ImmutableList.of(new Service1(), new Service2(), new Service3());

    assertThat(Dependencies.inOrder(ImmutableSet.copyOf(testingServices)))
        .has().exactlyAs(testingServices).inOrder();
  }

  @Test
  public void dependencyOnClassNotBoundShouldBeAllowed() {
    // Service 2 depends on Service 1 which is not included here.
    ImmutableList<TestingService> testingServices =
        ImmutableList.of(new Service2(), new Service3());

    assertThat(Dependencies.inOrder(ImmutableSet.copyOf(testingServices)))
        .has().exactlyAs(testingServices).inOrder();
  }

  @DependsOn({ServiceA.class, ServiceB.class}) private static class Last implements TestingService {}

  @Test
  public void multipleDependencies() {
    ImmutableList<TestingService> testingServices =
        ImmutableList.of(new ServiceA(), new ServiceB(), new Last());

    ImmutableList<TestingService> ordered =
        Dependencies.inOrder(ImmutableSet.copyOf(testingServices));

    assertThat(ordered).has().exactlyAs(testingServices);
    assertThat(Iterables.getLast(ordered)).isInstanceOf(Last.class);
  }

  @DependsOn(C.class) private static class A implements TestingService {}
  @DependsOn(A.class) private static class B implements TestingService {}
  @DependsOn(B.class) private static class C implements TestingService {}

  @Test
  public void throwsExceptionIfCyclePresent() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Cycle");
    Dependencies.inOrder(ImmutableSet.of(new A(), new B(), new C()));
  }
}
