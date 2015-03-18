# Acai

[![Build Status](https://travis-ci.org/google/acai.svg?branch=master)](
https://travis-ci.org/google/acai)

Acai makes it easy to write functional tests of your application
with JUnit4 and Guice.

Acai makes it simple to:
 - Inject the helper classes you need into tests
 - Start any services needed by your tests
 - Run between-test cleanup of these services
 - Start up multiple services for testing in the right order
 - Create test scoped bindings

Acai is designed for large functional tests of your application. For
example it can help with writing tests which start your backend and frontend
server in a self-contained mode with their dependencies faked out and then
validates some key user scenarios with Webdriver to give you confidence your
complete system works correctly. It can also be useful for tests which validate
the integration of a small set of components. Note however that for smaller
unit-tests we generally recommend you create the class under test manually
rather than using Acai.

## Installation
Add a dependency on `com.google.acai:acai` in your build system to fetch Acai
automatically from Maven Central. For example with Maven add the following to
your `pom.xml`:

```XML
<dependency>
  <groupId>com.google.acai</groupId>
  <artifactId>acai</artifactId>
  <version>0.1</version>
  <scope>test</scope>
</dependency>
```

See the [artifact details on Maven Central](
http://search.maven.org/#artifactdetails%7Ccom.google.acai%7Cacai%7C0.1%7Cjar)
for dependency information for other build systems or to simply download the
jars.

## Using Acai to inject a test
The simplest test using Acai doesn't register any TestingService bindings
at all, it just uses Acai to inject a test with a module:

```Java
@RunWith(JUnit4.class)
public class SimpleTest {
  @Rule public Acai acai = new Acai(MyTestModule.class);

  @Inject private MyClass foo;

  @Test
  public void checkSomethingWorks() {
    // Use the injected value of foo here
  }

  private static class MyTestModule extends AbstractModule {
    @Override protected void configure() {
      bind(MyClass.class).to(MyClassImpl.class);
    }
  }
}
```

## Using Acai to start services
The real power of Acai comes when your production server is configured
with Guice and you create an alternate test module which configures your server
with heavyweight dependencies like databases replaced with local in-memory
implementations. You could then start this server once for all tests in the
suite (to avoid waiting for it to start between each test) and wipe the
database between tests (to cheaply isolate test-cases from one-another).

The following example shows how this pattern would be used in tests:

```Java
@RunWith(JUnit4.class)
public class ExampleFunctionalTest {
  @Rule public Acai acai = new Acai(MyTestModule.class);

  @Inject private MyServerClient serverClient;

  @Test
  public void checkSomethingWorks() {
    // Call the running server and test some behaviour here.
    // Any state will be cleared by MyFakeDatabaseWiper after each
    // test case.
  }

  private static class MyTestModule extends AbstractModule {
    @Override protected void configure() {
      // Normal Guice modules which configure your
      // server with in-memory versions of backends.
      install(MyServerModule());
      install(MyFakeDatabaseModule());

      install(new TestingServiceModule() {
        @Override protected void configureTestingServices() {
          bindTestingService(MyServerRunner.class);
          bindTestingService(MyFakeDatabaseWiper.class);
        }
      });
    }
  }

  private static class MyServerRunner implements TestingService {
    @Inject private MyServer myServer;

    @BeforeSuite void startServer() {
      myServer.start().awaitStarted();
    }
  }

  private static class MyFakeDatabaseWiper implements TestingService {
    @Inject private MyFakeDatabse myFakeDatabase;

    @AfterTest void wipeDatabase() {
      myFakeDatabase.wipe();
    }
  }
}
```

Note that when a module is passed to Acai in a rule any @BeforeSuite
methods are only executed once per suite even if the same module is used in
multiple Acai rules in multiple different test classes within that suite.
This allows tests of the server to be structured into test classes according to
the functionality being tested.

## Services which depend upon each other
If the services you need to start for tests must be started in a specific order
you can express this using the `@DependsOn` annotation.

For example:

```Java
@RunWith(JUnit4.class)
public class ExampleFrontendWebdriverTest {
  @Rule public Acai acai = new Acai(MyTestModule.class);

  @Inject private SomeFrontendFeaturePageObject featurePage;

  @Test
  public void checkSomethingWorks() {
    // Test the frontend client using the webdriver page
    // object here.
  }

  private static class MyTestModule extends AbstractModule {
    @Override protected void configure() {
      // Normal Guice modules which configure your
      // server with in-memory versions of servers and
      // a test module configuring a webdriver client.
      install(MyServerModule());
      install(MyFakeDatabaseModule());
      install(WebDriverModule());

      install(new TestingServiceModule() {
        @Override protected void configureTestingServices() {
          bindTestingService(MyFrontendRunner.class);
          bindTestingService(MyBackendRunner.class);
        }
      });
    }
  }

  @DependsOn(MyBackendRunner.class)
  private static class MyFrontendRunner implements TestingService {
    @Inject private MyFrontendServer myFrontendServer;

    @BeforeSuite void startServer() {
      myFrontendServer.start().awaitStarted();
    }
  }

  private static class MyBackendRunner implements TestingService {
    @Inject private MyBackendServer myBackendServer;

    @BeforeSuite void startServer() {
      myBackendServer.start().awaitStarted();
    }
  }
}
```

In the above example `MyFrontendRunner` is annotated
`@DependsOn(MyBackendRunner.class)` which will cause Acai to start the
backend server before starting the frontend.

## Test scoped bindings
Occasionally you may wish to have one instance of a class per test and inject
this instance in multiple places in the object graph. In this case Guice's
default instance scope will not do. Fortunately Acai provides a `@TestScoped`
annotation which can be used to achieve exactly this.

For example we may define a module for using Webdriver (a popular browser
automation tool) in our tests like so:

```java
class WebdriverModule extends AbstractModule {
  private static final Duration MAX_WAIT = Duration.standardSeconds(5);

  @Override
  protected void configure() {
    install(new TestingServiceModule() {
      @Override protected void configureTestingServices() {
        bindTestingService(WebDriverQuitter.class);
      }
    });
  }

  @Provides
  @TestScoped
  WebDriver provideWebDriver() {
    // Provide the driver here; precisely one instance will be
    // created per test case.
  }

  @Provides
  WebDriverWait provideWait(WebDriver webDriver) {
    return new WebDriverWait(webDriver, MAX_WAIT.getStandardSeconds());
  }

  static class WebDriverQuitter implements TestingService {
    @Inject Provider<WebDriver> webDriver;

    @AfterTest void quitWebDriver() throws Exception {
      // Calling get on the Provider here returns the instance
      // for the test case which we are currently tearing down.
      webDriver.get().quit();
    }
  }
}
```

One important point to note when using `@TestScoped` bindings is that
`TestingService` instances are instantiated once for all tests outside of test
scope. Therefore if you wish to access `@TestScoped` bindings in a
`@BeforeTest` or `@AfterTest` method you should inject a `Provider` and call
`get` on it within those methods as shown in the above example.

## API
As shown in the above examples Acai has a relatively small API surface.
Firstly, and most importantly, there is the `Acai` rule class itself
which is used as a JUnit4 `@Rule` and is passed a module class to be used to
configure the test.

The module class passed to the `Acai` constructor may optionally use
`TestingServiceModule` to bind one or more `TestingService` implementations.

The `TestingService` interface is purely a marker to allow Acai to know
which classes provide testing services. To actually do anything implementations
of this interface should add zero argument methods annotated with one of
`@BeforeSuite`, `@BeforeTest` or `@AfterTest`. These methods will be run before
the suite, before each test or after each test respectively. You may add as
many methods annotated with these annotations as you wish to a
`TestingService`; Acai will find and run them all when appropriate.

For more advanced use-cases where instance scope is not sufficient the
`@TestScoped` annotation can be used to create one instance of a class per test
case.

Finally a `TestingService` implementation can be annotated `@DependsOn` to
signal its `@BeforeSuite` and `@BeforeTest` methods need to be run after
those of another `TestingService`. This provides a simple declarative mechanism
to order service startup in tests.

Refer to the examples above to see the API in action.

## Disclaimer
This is not an official Google product.
