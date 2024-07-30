package dev.ckateptb.container.exception;

import dev.ckateptb.container.Container;

import java.util.Deque;
import java.util.stream.Collectors;

public class CircularDependencyException extends ContainerException {
    public CircularDependencyException(Container.Key target, Deque<Container.Key> stacktrace) {
        super("Circular dependence was found in \n***\n" + stacktrace.stream()
                .map(key -> target.equals(key) ? " > " + key : key.toString())
                .collect(Collectors.joining("\n")) + "\n***\n");
    }
}
