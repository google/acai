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

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestScopeTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Mock private Statement statement;
  @Mock private FrameworkMethod frameworkMethod;

  @Test
  public void sameInstanceInjectedWithinTest() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    new Acai(EmptyTestModule.class).apply(statement, frameworkMethod, test).evaluate();

    assertThat(test.instanceOne).isNotNull();
    assertThat(test.instanceTwo).isSameInstanceAs(test.instanceOne);
  }

  @Test
  public void differentInstanceInjectedAcrossTests() throws Throwable {
    FakeTestClass testOne = new FakeTestClass();
    FakeTestClass testTwo = new FakeTestClass();

    new Acai(EmptyTestModule.class).apply(statement, frameworkMethod, testOne).evaluate();
    new Acai(EmptyTestModule.class).apply(statement, frameworkMethod, testTwo).evaluate();

    assertThat(testOne.instanceOne).isNotSameInstanceAs(testTwo.instanceOne);
  }

  @Test
  public void servicesInstantiatedOutsideTestScope() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    thrown.expect(ProvisionException.class);
    thrown.expectMessage("@TestScoped binding outside test");
    new Acai(InvalidTestModule.class).apply(statement, frameworkMethod, test).evaluate();
  }

  @Test
  public void canCallProviderInServiceSetupAndTeardown() throws Throwable {
    FakeTestClass test = new FakeTestClass();

    new Acai(ValidTestModule.class).apply(statement, frameworkMethod, test).evaluate();

    assertThat(ValidTestingService.valueFromBeforeTest).isNotNull();
    assertThat(ValidTestingService.valueFromAfterTest)
        .isSameInstanceAs(ValidTestingService.valueFromBeforeTest);
  }

  @Test
  public void parallelTestsReceiveDifferentInstances() throws Throwable {
    final FakeTestClass testOne = new FakeTestClass();
    FakeTestClass testTwo = new FakeTestClass();
    final CountDownLatch testStarted = new CountDownLatch(1);
    final CountDownLatch endTest = new CountDownLatch(1);

    Thread testOneThread =
        new Thread(
            () -> {
              try {
                new Acai(EmptyTestModule.class)
                    .apply(
                        new Statement() {
                          @Override
                          public void evaluate() throws Throwable {
                            testStarted.countDown();
                            endTest.await();
                          }
                        },
                        frameworkMethod,
                        testOne)
                    .evaluate();
              } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
              }
            });

    testOneThread.start();

    // Wait for test one to be running.
    testStarted.await();
    new Acai(EmptyTestModule.class).apply(statement, frameworkMethod, testTwo).evaluate();

    // Allow test one to complete now that testTwo has run in parallel.
    endTest.countDown();
    testOneThread.join();

    assertThat(testOne.instanceOne).isNotNull();
    assertThat(testOne.instanceOne).isNotNull();
    assertThat(testOne.instanceOne).isNotSameInstanceAs(testTwo.instanceOne);
  }

  @Test
  public void threadStartedByTestCanAccessSameInstance_injectedByChildThreadFirst()
      throws Throwable {
    FakeTestClassWithProvider test = new FakeTestClassWithProvider();
    AtomicReference<MyTestScopedClass> instanceInChildThread = new AtomicReference<>();
    AtomicReference<MyTestScopedClass> instanceInMainThread = new AtomicReference<>();

    new Acai(EmptyTestModule.class)
        .apply(
            new Statement() {
              @Override
              public void evaluate() throws Throwable {
                Thread thread = new Thread(() -> {
                  instanceInChildThread.set(test.provider.get());
                });
                thread.start();
                thread.join();
                instanceInMainThread.set(test.provider.get());
              }
            },
            frameworkMethod,
            test)
        .evaluate();

    assertThat(instanceInMainThread.get()).isSameInstanceAs(instanceInChildThread.get());
  }

  @Test
  public void threadStartedByTestCanAccessSameInstance_injectedByMainThreadFirst()
      throws Throwable {
    FakeTestClassWithProvider test = new FakeTestClassWithProvider();
    AtomicReference<MyTestScopedClass> instanceInChildThread = new AtomicReference<>();
    AtomicReference<MyTestScopedClass> instanceInMainThread = new AtomicReference<>();

    new Acai(EmptyTestModule.class)
        .apply(
            new Statement() {
              @Override
              public void evaluate() throws Throwable {
                instanceInMainThread.set(test.provider.get());
                Thread thread = new Thread(() -> {
                  instanceInChildThread.set(test.provider.get());
                });
                thread.start();
                thread.join();
              }
            },
            frameworkMethod,
            test)
        .evaluate();

    assertThat(instanceInMainThread.get()).isSameInstanceAs(instanceInChildThread.get());
  }

  @Test
  public void threadStartedByTestCanNoLongerAccessTestScopeAfterTestFinished()
      throws Throwable {
    FakeTestClassWithProvider test = new FakeTestClassWithProvider();
    AtomicReference<Thread> childThreadHolder = new AtomicReference<>();
    AtomicReference<Exception> thrownExceptionHolder = new AtomicReference<>();
    final CountDownLatch endTest = new CountDownLatch(1);

    new Acai(EmptyTestModule.class)
        .apply(
            new Statement() {
              @Override
              public void evaluate() throws Throwable {
                Thread thread = new Thread(() -> {
                  try {
                    endTest.await();
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  try {
                    test.provider.get();
                  } catch (Exception e) {
                    thrownExceptionHolder.set(e);
                  }
                });
                thread.start();
                childThreadHolder.set(thread);
              }
            },
            frameworkMethod,
            test)
        .evaluate();

    // Unblock the child thread, that will then execute the provider.get() call.
    endTest.countDown();
    childThreadHolder.get().join();

    Exception e = thrownExceptionHolder.get();
    assertThat(e).isInstanceOf(ProvisionException.class);
    assertThat(e).hasMessageThat().contains("@TestScoped binding outside test");
  }

  private static class EmptyTestModule extends AbstractModule {
    @Override
    protected void configure() {
      // No-op.
    }
  }

  private static class InvalidTestModule extends TestingServiceModule {
    @Override
    protected void configureTestingServices() {
      bindTestingService(InvalidTestingService.class);
    }
  }

  private static class ValidTestModule extends TestingServiceModule {
    @Override
    protected void configureTestingServices() {
      bindTestingService(ValidTestingService.class);
    }
  }

  private static class FakeTestClass {
    @Inject MyTestScopedClass instanceOne;
    @Inject MyTestScopedClass instanceTwo;
  }

  private static class FakeTestClassWithProvider {
    @Inject Provider<MyTestScopedClass> provider;
  }

  @TestScoped
  private static class MyTestScopedClass {}

  private static class InvalidTestingService implements TestingService {
    @Inject MyTestScopedClass testScoped;
  }

  private static class ValidTestingService implements TestingService {
    @Inject Provider<MyTestScopedClass> testScoped;
    static MyTestScopedClass valueFromBeforeTest;
    static MyTestScopedClass valueFromAfterTest;

    @BeforeTest
    void beforeTest() {
      valueFromBeforeTest = testScoped.get();
    }

    @AfterTest
    void afterTest() {
      valueFromAfterTest = testScoped.get();
    }
  }
}
