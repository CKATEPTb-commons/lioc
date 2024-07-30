package dev.ckateptb.container;

import dev.ckateptb.container.annotation.*;
import dev.ckateptb.container.exception.BeanCreationException;
import dev.ckateptb.container.exception.CircularDependencyException;
import dev.ckateptb.container.exception.NoSuchBeanDefinitionException;
import dev.ckateptb.container.handler.ComponentRegisterHandler;
import dev.ckateptb.container.handler.ContainerInitializeHandler;
import dev.ckateptb.container.util.PackageScanner;
import dev.ckateptb.reflection.Reflect;
import dev.ckateptb.reflection.ReflectMethod;
import lombok.Setter;
import lombok.SneakyThrows;

import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Setter
public class Container<O> {
    private final String INVALID_POST_CONSTRUCT = "The %s method in %s annotated as @PostConstruct must not contain parameters!";
    private final String POST_CONSTRUCT_ERROR = "The %s method in %s annotated as @PostConstruct threw an error!";
    private final String BEAN_CREATE_ERROR = "Failed to create Bean %s, the initiator %s#%s threw an error.";
    private final String BEAN_DEFINE_ERROR = "Could not find an implementation for class %s with qualifier %s inside the container, maybe it is not a component.";
    private final Set<Key> declared = new HashSet<>();
    private final Map<Key, O> owners = new HashMap<>();
    private final Map<Key, BeanParent> parents = new HashMap<>();
    private final Map<Key, Object> beans = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ComponentRegisterHandler<O>> componentRegisterHandlers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ContainerInitializeHandler<O>> containerInitializedHandlers = new ConcurrentLinkedQueue<>();
    private BiConsumer<O, Runnable> componentRegisterHandlerExecutor = (o, runnable) -> runnable.run();
    private Consumer<String> warnLogger = System.out::println;
    private BiConsumer<String, Throwable> errorLogger = (message, throwable) -> {
        System.err.println(message);
        throwable.printStackTrace();
    };

    public void register(O owner, Object bean) {
        this.register(owner, bean, Qualifier.DEFAULT_QUALIFIER);
    }

    public void register(O owner, Object bean, String qualifier) {
        this.owners.put(Key.of(bean.getClass(), qualifier), owner);
        this.register(bean, qualifier);
    }

    public void registerSilent(O owner, Object bean) {
        this.registerSilent(owner, bean, Qualifier.DEFAULT_QUALIFIER);
    }

    public void registerSilent(O owner, Object bean, String qualifier) {
        this.owners.put(Key.of(bean.getClass(), qualifier), owner);
        this.registerSilent(bean, qualifier);
    }

    private void register(Object bean, String qualifier) {
        this.registerSilent(bean, qualifier);
        this.postConstruct(bean);
        this.emitHandlers(bean, qualifier, this.owners.get(Key.of(bean.getClass(), qualifier)));
    }

    private void registerSilent(Object bean, String qualifier) {
        this.beans.put(Key.of(bean.getClass(), qualifier), bean);
    }

    public <T> Optional<T> findBean(Class<T> clazz) {
        return this.findBean(clazz, Qualifier.DEFAULT_QUALIFIER);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> findBean(Class<T> clazz, String qualifier) {
        return Optional.ofNullable(this.beans.get(Key.of(clazz, qualifier)))
                .map(o -> (T) o);
    }

    private <T> Key findImplementation(Key key) {
        return this.declared.stream()
                .filter(v -> v.qualifier.equals(key.qualifier))
                .filter(v -> key.clazz.isAssignableFrom(v.clazz))
                .findFirst().orElse(key);
    }

    public void scan(O owner) {
        this.scan(owner, (s) -> true);
    }

    public void scan(O owner, Predicate<String> filter) {
        Class<?> ownerClass = owner.getClass();
        this.scan(owner, Reflect.jarOf(ownerClass)
                .getClassesSneaky(ownerClass.getClassLoader(), filter));
    }

    public void scan(O owner, String root) {
        this.scan(owner, root, (s) -> true);
    }

    public void scan(O owner, String root, Predicate<String> filter) {
        this.scan(owner, PackageScanner.getClassesInPackage(root)
                .stream()
                .map(Reflect::on)
                .filter(reflect -> filter.test(reflect.type().getName()))
                .collect(Collectors.toUnmodifiableSet()));
    }

    private void scan(O owner, Collection<Reflect<?>> classes) {
        this.registerSilent(owner, owner);
        classes.stream()
                .filter(reflect -> reflect.type().isAnnotationPresent(Component.class))
                .forEach(reflect -> {
                    Key component = Key.of(reflect.type());
                    Set<Key> set = new HashSet<>();
                    set.add(component);
                    reflect.methodsAnnotated(Bean.class).forEach(methodReflect -> {
                        ReflectMethod method = methodReflect.getMethod();
                        String beanQualifier = method.getAnnotation(Bean.class).value();
                        Key bean = Key.of(method.getReturnType(), beanQualifier);
                        this.parents.put(bean, new BeanParent(component, method));
                        set.add(bean);
                    });
                    set.forEach(key -> this.putOwnerRecursive(owner, key, new LinkedList<>()));
                    this.declared.addAll(set);
                });
    }

    public void initialize() {
        Map<Object, String> components = new HashSet<>(this.owners.keySet()).stream()
                .map(key -> new QualifiedBean(this.initialize(key), key.qualifier()))
                .collect(Collectors.toMap(QualifiedBean::bean, QualifiedBean::qualifier));
        this.containerInitializedHandlers.forEach(handler -> {
            try {
                handler.handle(this, components);
            } catch (Throwable throwable) {
                errorLogger.accept("Container Initialize handler throws an error", throwable);
            }
        });
    }

    @SneakyThrows
    private Object initialize(Key key) {
        key = this.findImplementation(key);
        Class<?> clazz = key.clazz();
        String qualifier = key.qualifier();
        if (!clazz.isAnnotationPresent(Component.class) && !this.parents.containsKey(key)) {
            String message = String.format(BEAN_DEFINE_ERROR, clazz, qualifier);
            throw new NoSuchBeanDefinitionException(message);
        }
        Optional<?> optional = this.findBean(clazz, qualifier);
        if (optional.isPresent()) return optional.get();
        Function<Object[], Object> mapper;
        Collection<Key> parameters;
        if (this.isBean(key)) {
            BeanParent parent = this.parents.get(key);
            Object owner = this.initialize(parent.parent);
            ReflectMethod method = parent.method;
            parameters = this.findParameters(method);
            mapper = (params) -> {
                try {
                    return method.invoke(owner, params);
                } catch (Throwable throwable) {
                    Class<?> returnType = method.getReturnType();
                    String message = String.format(BEAN_CREATE_ERROR, returnType, owner.getClass(), method.getName());
                    throw new BeanCreationException(message, throwable);
                }
            };
        } else {
            Reflect.ConstructorReflect<?> constructor = this.findConstructor(clazz);
            parameters = this.findParameters(constructor);
            mapper = (params) -> constructor.newInstance(params).get();
        }
        Object[] array = parameters.stream().map(this::initialize).toArray(Object[]::new);
        Object instance = mapper.apply(array);
        this.register(instance, qualifier);
        return instance;
    }


    private void putOwnerRecursive(O owner, Key key, Deque<Key> stacktrace) {
        if (stacktrace.contains(key)) {
            stacktrace.push(key);
            throw new CircularDependencyException(key, stacktrace);
        }
        if (this.owners.containsKey(key)) return;
        stacktrace.push(key);
        if (this.isBean(key)) this.putOwnerRecursive(owner, this.parents.get(key).parent, stacktrace);
        this.owners.put(key, owner);
        this.findParameters(key).forEach(v -> this.putOwnerRecursive(owner, v, stacktrace));
        stacktrace.pop();
    }

    private Collection<Key> findParameters(Key key) {
        if (Reflect.classOf(key.clazz).isInterface()) return Collections.emptySet();
        if (!this.isBean(key)) return this.findParameters(key.clazz);
        return this.findParameters(this.parents.get(key).method());
    }

    private Collection<Key> findParameters(Class<?> clazz) {
        return this.findParameters(this.findConstructor(clazz));
    }

    private Collection<Key> findParameters(ReflectMethod method) {
        return this.findParameters(method.getParameters());
    }

    private Collection<Key> findParameters(Reflect.ConstructorReflect<?> reflect) {
        return this.findParameters(reflect.getConstructor().getParameters());
    }

    private Collection<Key> findParameters(Parameter[] parameters) {
        return Arrays.stream(parameters).map(Key::of).toList();
    }

    private <T> Reflect.ConstructorReflect<T> findConstructor(Class<T> clazz) {
        return Reflect.on(clazz).map(reflect -> reflect.constructorAnnotated(Autowired.class)
                .stream()
                .findFirst()
                .orElseGet(reflect::constructor)).asConstructor();
    }

    private boolean isBean(Key key) {
        return this.parents.containsKey(key);
    }

    private void postConstruct(Object bean) {
        Reflect.on(bean)
                .methodsAnnotated(PostConstruct.class)
                .forEach(reflect -> {
                    ReflectMethod method = reflect.getMethod();
                    String methodName = method.getName();
                    String name = bean.getClass().getName();
                    if (method.getParameters().length > 0) {
                        String message = String.format(INVALID_POST_CONSTRUCT, methodName, name);
                        this.warnLogger.accept(message);
                    } else {
                        try {
                            reflect.call();
                        } catch (Throwable throwable) {
                            String message = String.format(POST_CONSTRUCT_ERROR, methodName, name);
                            this.errorLogger.accept(message, throwable);
                        }
                    }
                });
    }

    private <T> void emitHandlers(T bean, String qualifier, O owner) {
        this.componentRegisterHandlers.forEach(handler -> {
            Runnable runnable = () -> {
                try {
                    handler.handle(bean, qualifier, owner);
                } catch (Throwable throwable) {
                    errorLogger.accept("Component register handler throws an error", throwable);
                }
            };
            this.componentRegisterHandlerExecutor.accept(owner, runnable);
        });
    }

    public void addComponentRegisterHandler(ComponentRegisterHandler<O> handler) {
        this.componentRegisterHandlers.add(handler);
    }

    public void removeComponentRegisterHandler(ComponentRegisterHandler<O> handler) {
        this.componentRegisterHandlers.remove(handler);
    }

    public void addContainerInitializedHandler(ContainerInitializeHandler<O> handler) {
        this.containerInitializedHandlers.add(handler);
    }

    public void removeContainerInitializedHandler(ContainerInitializeHandler<O> handler) {
        this.containerInitializedHandlers.remove(handler);
    }

    public record Key(Class<?> clazz, String qualifier) {
        static Key of(Parameter parameter) {
            Class<?> component = parameter.getType();
            AtomicReference<String> qualifier = new AtomicReference<>(Qualifier.DEFAULT_QUALIFIER);
            Qualifier qualifierAnnotation = parameter.getAnnotation(Qualifier.class);
            if (qualifierAnnotation != null) {
                qualifier.set(qualifierAnnotation.value());
            } else {
                Component componentAnnotation = component.getAnnotation(Component.class);
                if (componentAnnotation != null) qualifier.set(componentAnnotation.value());
            }
            return of(component, qualifier.get());
        }

        static Key of(Class<?> component) {
            Component annotation = component.getAnnotation(Component.class);
            return of(component, annotation.value());
        }

        static Key of(Class<?> component, String qualifier) {
            return new Key(component, qualifier);
        }
    }

    private record BeanParent(Key parent, ReflectMethod method) {

    }

    private record QualifiedBean(Object bean, String qualifier) {

    }
}
