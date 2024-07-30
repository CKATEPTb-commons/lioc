package dev.ckateptb.container.handler;


import dev.ckateptb.container.Container;

import java.util.Map;

public interface ContainerInitializeHandler<T> {
    void handle(Container<T> container, Map<Object, String> components);
}
