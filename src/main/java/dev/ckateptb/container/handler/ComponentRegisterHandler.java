package dev.ckateptb.container.handler;


public interface ComponentRegisterHandler<T> {
    void handle(Object component, String qualifier, T owner);

    default boolean onEnable() {
        return true;
    }
}
