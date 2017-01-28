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
import static com.google.common.truth.Truth.assert_;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.auto.value.AutoValue;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.Provides;
import java.lang.annotation.Retention;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AcaiTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Mock private Statement statement;
  @Mock private FrameworkMethod frameworkMethod;
  @Mock private Service testingService;

  @Before
  public void cleanup() {
    Service.methodCalls = MethodCalls.create();
    DependentService.methodCalls = MethodCalls.create();
    ServiceWithFailingBeforeTest.shouldFail = true;
    Acai.testOnlyResetEnvironments();
  }

  @Test
  public void serviceMethodsRun() throws Throwable {
    Acai acai = new Acai(TestModule.class);

    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create());

    acai.apply(
            new Statement() {
              @Override
              public void evaluate() throws Throwable {
                assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 1, 0));
              }
            },
            frameworkMethod,
            new Object())
        .evaluate();

    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 1, 1));
  }

  @Test
  public void beforeSuiteRunOnceOnly() throws Throwable {
    new Acai(TestModule.class).apply(statement, frameworkMethod, new Object()).evaluate();
    new Acai(TestModule.class).apply(statement, frameworkMethod, new Object()).evaluate();

    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 2, 2));
  }

  @Test
  public void testCaseIsInjected() throws Throwable {
    ExampleTest test = new ExampleTest();

    new Acai(TestModule.class).apply(statement, frameworkMethod, test).evaluate();

    assertThat(test.injected).isEqualTo("injected-value");
  }

  @Test
  public void failingTestInjectionDoesNotAffectSubsequentTests() throws Throwable {
    Acai acai = new Acai(TestModule.class);
    try {
      acai.apply(statement, frameworkMethod, new TestWithUnsatisfiedBinding()).evaluate();
      assert_().fail("Expected ConfigurationException to be thrown.");
    } catch (ConfigurationException e) {
      // Expected: TestWithUnsatisfiedBinding requires binding not satisfied by TestModule.
    }

    acai.apply(statement, frameworkMethod, new ExampleTest()).evaluate();

    verify(statement, times(1)).evaluate();
  }

  @Test
  public void failingBeforeTestMethodDoesNotAffectSubsequentTests() throws Throwable {
    Acai acai = new Acai(FailingBeforeTestModule.class);
    try {
      acai.apply(statement, frameworkMethod, new ExampleTest()).evaluate();
      assert_().fail("Expected TestException to be thrown.");
    } catch (TestException e) {
      // Expected: ServiceWithFailingBeforeTest throws TestException in @BeforeTest.
    }

    ServiceWithFailingBeforeTest.shouldFail = false;
    acai.apply(statement, frameworkMethod, new ExampleTest()).evaluate();
    verify(statement, times(1)).evaluate();
  }

  @Test
  public void testsAreInjectedAfterRunningBeforeSuite() throws Throwable {
    ExampleTest test = new ExampleTest();

    new Acai(TestModule.class).apply(statement, frameworkMethod, test).evaluate();

    // Check injection happened after Service.initialized was set to true
    // by @BeforeSuite method.
    assertThat(test.initialized).isEqualTo(true);
  }

  @Test
  public void servicesRunInDependencyOrder() throws Throwable {
    new Acai(DependentServiceModule.class)
        .apply(statement, frameworkMethod, new Object())
        .evaluate();

    // Sanity check the services ran, the ordering assertions are
    // within DependentService itself.
    assertThat(DependentService.methodCalls).isEqualTo(MethodCalls.create(1, 1, 1));
    assertThat(Service.methodCalls).isEqualTo(MethodCalls.create(1, 1, 1));
  }

  @Test
  public void usefulErrorMessageWhenModuleMissingZeroArgConstructor() throws Throwable {
    thrown.expectMessage("does not have zero argument constructor");
    new Acai(ModuleWithoutZeroArgumentConstructor.class)
        .apply(statement, frameworkMethod, new Object())
        .evaluate();
  }

  @Test
  public void rethrowsExceptionThrownByModuleConstructor() throws Throwable {
    thrown.expect(TestException.class);
    new Acai(ModuleWithThrowingConstructor.class)
        .apply(statement, frameworkMethod, new Object())
        .evaluate();
  }

  private static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bindConstant().annotatedWith(TestBindingAnnotation.class).to("injected-value");
      bind(Service.class).in(Singleton.class);
      install(
          new TestingServiceModule() {
            @Override
            protected void configureTestingServices() {
              bindTestingService(Service.class);
            }
          });
    }

    @Provides
    @IsInitialized
    boolean provideIsInitialized(Service service) {
      return service.initialized;
    }
  }

  private static class DependentServiceModule extends AbstractModule {
    @Override
    protected void configure() {
      install(
          new TestingServiceModule() {
            @Override
            protected void configureTestingServices() {
              bindTestingService(DependentService.class);
              bindTestingService(Service.class);
            }
          });
    }

    @Provides
    @IsInitialized
    boolean provideIsInitialized(Service service) {
      return service.initialized;
    }
  }

  private static class Service implements TestingService {
    static MethodCalls methodCalls = MethodCalls.create();
    boolean initialized = false;

    @BeforeSuite
    private void beforeSuite() {
      initialized = true;
      methodCalls = methodCalls.incrementBeforeSuite();
    }

    @BeforeTest
    private void beforeTest() {
      methodCalls = methodCalls.incrementBeforeTest();
    }

    @AfterTest
    private void afterTest() {
      methodCalls = methodCalls.incrementAfterTest();
    }
  }

  @DependsOn(Service.class)
  private static class DependentService implements TestingService {
    static MethodCalls methodCalls = MethodCalls.create();

    @BeforeSuite
    private void beforeSuite() {
      assert_()
          .withFailureMessage("DependentService should be run after Service")
          .that(Service.methodCalls.beforeSuite())
          .isEqualTo(methodCalls.beforeSuite() + 1);
      methodCalls = methodCalls.incrementBeforeSuite();
    }

    @BeforeTest
    private void beforeTest() {
      assert_()
          .withFailureMessage("DependentService should be run after Service")
          .that(Service.methodCalls.beforeTest())
          .isEqualTo(methodCalls.beforeTest() + 1);
      methodCalls = methodCalls.incrementBeforeTest();
    }

    @AfterTest
    private void afterTest() {
      methodCalls = methodCalls.incrementAfterTest();
      assert_()
          .withFailureMessage("Service should be cleaned up after DependentService")
          .that(Service.methodCalls.afterTest())
          .isEqualTo(methodCalls.afterTest() - 1);
    }
  }

  @AutoValue
  abstract static class MethodCalls {
    abstract int beforeSuite();

    abstract int beforeTest();

    abstract int afterTest();

    static MethodCalls create() {
      return new AutoValue_AcaiTest_MethodCalls(0, 0, 0);
    }

    static MethodCalls create(int beforeSuite, int beforeTest, int afterTest) {
      return new AutoValue_AcaiTest_MethodCalls(beforeSuite, beforeTest, afterTest);
    }

    MethodCalls incrementBeforeSuite() {
      return create(beforeSuite() + 1, beforeTest(), afterTest());
    }

    MethodCalls incrementBeforeTest() {
      return create(beforeSuite(), beforeTest() + 1, afterTest());
    }

    MethodCalls incrementAfterTest() {
      return create(beforeSuite(), beforeTest(), afterTest() + 1);
    }
  }

  private static class TestWithUnsatisfiedBinding {
    @Inject @TestBindingAnnotation ExampleTest unsatisfiedBinding;
  }

  private static class ExampleTest {
    @Inject @TestBindingAnnotation String injected;
    @Inject @IsInitialized boolean initialized;
  }

  private static class TestException extends RuntimeException {}

  private static class ServiceWithFailingBeforeTest implements TestingService {
    static boolean shouldFail = true;

    @BeforeTest
    void failingBeforeTest() {
      if (shouldFail) {
        throw new TestException();
      }
    }
  }

  private static class FailingBeforeTestModule extends TestingServiceModule {
    @Override
    protected void configureTestingServices() {
      install(new TestModule());
      bindTestingService(ServiceWithFailingBeforeTest.class);
    }
  }

  private static class ModuleWithoutZeroArgumentConstructor extends AbstractModule {
    ModuleWithoutZeroArgumentConstructor(String argument) {
      // No-op.
    }

    @Override
    protected void configure() {
      // No-op.
    }
  }

  private static class ModuleWithThrowingConstructor extends AbstractModule {
    ModuleWithThrowingConstructor() {
      throw new TestException();
    }

    @Override
    protected void configure() {
      // No-op.
    }
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  private @interface TestBindingAnnotation {}

  @Retention(RUNTIME)
  @BindingAnnotation
  private @interface IsInitialized {}
}
