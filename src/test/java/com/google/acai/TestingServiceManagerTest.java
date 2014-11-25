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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class TestingServiceManagerTest {

  @Test
  public void callBeforeSuiteMethod() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeSuite();

    assertThat(testingService.beforeSuiteCount).is(1);
    assertThat(testingService.beforeTestCount).is(0);
    assertThat(testingService.afterTestCount).is(0);
  }

  @Test
  public void callBeforeTestMethod() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeTest();

    assertThat(testingService.beforeSuiteCount).is(0);
    assertThat(testingService.beforeTestCount).is(1);
    assertThat(testingService.afterTestCount).is(0);
  }

  @Test
  public void callAfterTestMethod() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).afterTest();

    assertThat(testingService.beforeSuiteCount).is(0);
    assertThat(testingService.beforeTestCount).is(0);
    assertThat(testingService.afterTestCount).is(1);
  }

  @Test
  public void privateMethodsAreCalled() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeTest();

    assertThat(testingService.privateBeforeTestCount).is(1);
  }

  @Test
  public void methodsWithParametersAreIgnored() {
    MyTestingService testingService = new MyTestingService();

    new TestingServiceManager(testingService).beforeTest();

    assertThat(testingService.beforeTestWithParameterCount).is(0);
  }

  private static class MyTestingService implements TestingService {
    int beforeSuiteCount = 0;
    int beforeTestCount = 0;
    int afterTestCount = 0;
    int privateBeforeTestCount = 0;
    int beforeTestWithParameterCount = 0;

    @BeforeSuite
    public void incrementBeforeSuiteCount() {
      beforeSuiteCount++;
    }

    @BeforeTest
    public void incrementBeforeTestCount() {
      beforeTestCount++;
    }

    @BeforeTest
    private void incrementPrivateBeforeTestCount() {
      privateBeforeTestCount++;
    }

    @BeforeTest
    private void incrementBeforeTestWithParameterCount(int someParameter) {
      beforeTestWithParameterCount++;
    }

    @AfterTest
    public void incrementAfterTestCount() {
      afterTestCount++;
    }
  }
}
