<p align="center">
<h3 align="center">lioc</h3>

------

<p align="center">
This project provides a lightweight IoC (Inversion of Control) container implementation, inspired by the Spring Framework. The container manages the lifecycle of components, handles dependencies, and allows for the registration and retrieval of beans.
</p>

<p align="center">
<img alt="License" src="https://img.shields.io/github/license/CKATEPTb-commons/lioc">
<a href="https://docs.gradle.org/7.5/release-notes.html"><img src="https://img.shields.io/badge/Gradle-7.4-brightgreen.svg?colorB=469C00&logo=gradle"></a>
<a href="https://discord.gg/P7FaqjcATp" target="_blank"><img alt="Discord" src="https://img.shields.io/discord/925686623222505482?label=discord"></a>
<a href="https://repo.jyraf.com/service/rest/v1/search/assets/download?sort=version&repository=maven-snapshots&maven.groupId=dev.ckateptb&maven.artifactId=lioc&maven.extension=jar" target="_blank"><img alt="Download" src="https://img.shields.io/nexus/s/dev.ckateptb/lioc?server=https%3A%2F%2Frepo.jyraf.com"></a>
</p>

------

# Versioning

We use [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) to manage our releases.

# Features
- [X] **Lightweight:** Designed to be minimalistic and easy to use, without the overhead of more complex frameworks.
- [X] **Component Scanning:** Automatically scans packages to discover components and beans.
- [X] **Dependency Injection:** Supports constructor and method injection.
- [X] **Flexible Lifecycle Management:** Handles the initialization and destruction of beans, including methods annotated with `@PostConstruct`. The lifecycle is flexible due to event-driven mechanisms that allow developers to hook into different stages of the container's lifecycle.
- [X] **Qualifier Support:** Allows multiple implementations of the same interface with qualifiers.
- [X] **Circular Dependency Detection:** Detects and prevents circular dependencies.


------

# Installation

To use the lioc library, include it in your project as a dependency.

```groovy
repositories {
    maven("https://repo.jyraf.com/repository/maven-snapshots/")
}

dependencies {
    implementation("dev.ckateptb:lioc:<version>")
}
```

------

# Usage

## Basic Setup

To use the container, instantiate it and call the appropriate methods to register and retrieve beans.

```java
Container<MyApplication> container = new Container<>();
container.scan(myAppInstance, "com.example.mypackage", packetNameFilter); // for scan class-path
container.scan(myAppInstance, packetNameFilter); // or for scan source-jar
container.initialize(); // initialize the container by creating instances of all bins and components
```

### Manual Bean Registration

You can register beans manually or let the container scan for them. The container supports both explicit and silent registration. Using `registerSilent` will not trigger lifecycle events, unlike the standard `register` method.

```java
container.register(myOwner, new MyBean());
container.register(myOwner, new MyBean(), "customQualifier");

container.registerSilent(myOwner, new MyBean());
container.registerSilent(myOwner, new MyBean(), "customQualifier");
```

### Retrieving Beans

Beans can be retrieved by their class type and optional qualifier.

```java
Optional<MyBean> myBean = container.findBean(MyBean.class);
Optional<MyBean> myBeanWithQualifier = container.findBean(MyBean.class, "customQualifier");
```

## Event-Driven Lifecycle Management

The container's lifecycle is flexible and can be extended through event-driven mechanisms. Custom handlers can be added to hook into various stages of the lifecycle, providing a flexible way to manage components and their dependencies.

### Custom Handlers

* **ComponentRegisterHandler:** Handles events when a component is registered.
* **ContainerInitializeHandler:** Handles events after the container is initialized.

### Adding Handlers

You can add handlers to respond to specific lifecycle events:

```java
container.addComponentRegisterHandler((bean, qualifier, owner) -> {
    // Custom logic when a component is registered
});

container.addContainerInitializedHandler((container, components) -> {
    // Custom logic after the container is initialized
});
```

# Example

## Entry point

```java
public class MyApplication {
    public static void main(String[] args) {
        Container<MyApplication> container = new Container<>();
        MyApplication app = new MyApplication();
        container.scan(app);
        container.initialize();

        MyBean myBean = container.findBean(MyBean.class).orElseThrow();
        myBean.doSomething();
    }
}
```

## Component Creation

In this IoC container, you can define components similarly to how you would in a Spring application. Here's an example that demonstrates creating a component, a bean method, and dependency injection.

### Defining a Component

To define a component, use the `@Component` annotation. This annotation marks the class as a component, which the container will manage.

```java
@Component
public class MyComponent {

    private final MyDependency myDependency;

    // Constructor injection
    @Autowired
    public MyComponent(MyDependency myDependency) {
        this.myDependency = myDependency;
    }

    // Bean method
    @Bean
    public MyService myService() {
        return new MyService();
    }

    // Call method after constructor
    @PostConstruct
    public void doSomething() {
        myDependency.performTask();
        myService().executeService();
    }
}
```

### Defining a Dependency

Dependencies can also be components/beans or simple classes managed by the container.

```java
@Component
public class MyDependency {

    public void performTask() {
        System.out.println("Task performed by MyDependency.");
    }
}
```

### Bean Method

The `@Bean` annotation can be used on a method within a component to indicate that the method returns a bean that should be managed by the container.

```java
public class MyService {

    public void executeService() {
        System.out.println("Service executed by MyService.");
    }
}
```

In this example, `MyComponent` is a component managed by the container. It has a dependency on `MyDependency`, which is injected via the constructor. The `myService` method is annotated with `@Bean`, indicating that it produces a bean of type `MyService`. When the container initializes, it automatically creates and wires these components, allowing you to use them in your application

------

# License
This project is licensed under the [GPL-3.0 license](https://github.com/CKATEPTb-commons/lioc/blob/development/LICENSE.md).

# Contributing
Contributions are welcome! Feel free to open an issue or submit a pull request.