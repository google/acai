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
import static org.hamcrest.Matchers.isA;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestingServiceManagerTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void callBeforeClassMethod() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeClass();

    assertThat(testingService.beforeClassCount).isEqualTo(1);
    assertThat(testingService.beforeTestCount).isEqualTo(0);
    assertThat(testingService.afterTestCount).isEqualTo(0);
  }

  @Test
  public void callBeforeTestMethod() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeTest();

    assertThat(testingService.beforeClassCount).isEqualTo(0);
    assertThat(testingService.beforeTestCount).isEqualTo(1);
    assertThat(testingService.afterTestCount).isEqualTo(0);
  }

  @Test
  public void callAfterTestMethod() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).afterTest();

    assertThat(testingService.beforeClassCount).isEqualTo(0);
    assertThat(testingService.beforeTestCount).isEqualTo(0);
    assertThat(testingService.afterTestCount).isEqualTo(1);
  }

  @Test
  public void privateMethodsAreCalled() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeTest();

    assertThat(testingService.privateBeforeTestCount).isEqualTo(1);
  }

  @Test
  public void methodsWithParametersAreIgnored() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeTest();

    assertThat(testingService.beforeTestWithParameterCount).isEqualTo(0);
  }

  @Test
  public void methodsFoundThroughSubclass() {
    MyTestingService testingService = new MyTestingService() {};

    new TestingServiceManager(testingService).beforeTest();

    assertThat(testingService.beforeClassCount).isEqualTo(0);
    assertThat(testingService.beforeTestCount).isEqualTo(1);
    assertThat(testingService.afterTestCount).isEqualTo(0);
  }

  @Test
  public void runtimeExceptionsPropagated() {
    thrown.expect(TestRuntimeException.class);
    new TestingServiceManager(
            new TestingService() {
              @Before
              void beforeTest() {
                throw new TestRuntimeException();
              }
            })
        .beforeTest();
  }

  @Test
  public void checkedExceptionsPropagatedInsideRuntimeException() {
    thrown.expectCause(isA(TestException.class));
    new TestingServiceManager(
            new TestingService() {
              @Before
              void beforeTest() throws TestException {
                throw new TestException();
              }
            })
        .beforeTest();
  }

  @Test
  public void staticMethodsNotInvoked() {
    new TestingServiceManager(new ServiceWithStaticMethod()).beforeClass();
    assertThat(ServiceWithStaticMethod.staticBeforeClassMethodInvoked).isFalse();
  }

  private static class TestRuntimeException extends RuntimeException {}

  private static class TestException extends Exception {}

  private static class MyTestingService implements TestingService {
    int beforeClassCount = 0;
    int beforeTestCount = 0;
    int afterTestCount = 0;
    int privateBeforeTestCount = 0;
    int beforeTestWithParameterCount = 0;

    @BeforeClass
    public void incrementBeforeClassCount() {
      beforeClassCount++;
    }

    @Before
    public void incrementBeforeTestCount() {
      beforeTestCount++;
    }

    @Before
    private void incrementPrivateBeforeTestCount() {
      privateBeforeTestCount++;
    }

    @Before
    private void incrementBeforeTestWithParameterCount(int someParameter) {
      beforeTestWithParameterCount++;
    }

    @After
    public void incrementAfterTestCount() {
      afterTestCount++;
    }
  }

  private static class ServiceWithStaticMethod implements TestingService {
    static boolean staticBeforeClassMethodInvoked = false;

    @BeforeClass
    static void willNotBeInvoked() {
      staticBeforeClassMethodInvoked = true;
    }
  }
}
