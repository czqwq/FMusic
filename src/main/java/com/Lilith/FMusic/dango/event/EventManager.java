package com.Lilith.FMusic.dango.event;

import com.Lilith.FMusic.dango.DangoLogger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventManager {
    private final Map<Class<? extends Event>, List<ListenerHook>> listenerStore = new HashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public void addListener(Listener listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            method.setAccessible(true);
            EventHandler config = method.getAnnotation(EventHandler.class);
            if (config == null) continue;

            Parameter[] parameters = method.getParameters();
            if (parameters.length != 1) {
                DangoLogger.warn("@EventHandler method must have exactly one parameter -> {}", listener.getClass().getName());
                continue;
            }
            Parameter parameter = parameters[0];
            Class<?> type = parameter.getType();
            if (!Event.class.isAssignableFrom(type)) {
                DangoLogger.warn("@EventHandler method parameter must be an Event -> {}", listener.getClass().getName());
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClazz = (Class<? extends Event>) type;

            try {
                MethodHandle handle = LOOKUP.unreflect(method)
                        .bindTo(listener);
                List<ListenerHook> hooks = listenerStore
                        .computeIfAbsent(eventClazz, k -> new ArrayList<>());
                hooks.add(new ListenerHook(handle, config));
                hooks.sort((a, b) ->
                        Integer.compare(b.config.priority(), a.config.priority())
                );
            } catch (IllegalAccessException e) {
                DangoLogger.warn("Failed to create MethodHandle for method: {} -> {}", method.getName(), e.getMessage());
            }
        }
        DangoLogger.info("Listener registered successfully -> {}", listener.getClass().getName());
    }

    public void callEvent(Event event) {
        List<ListenerHook> list = listenerStore.get(event.getClass());
        if (list == null) {
            return;
        }

        for (ListenerHook listenerHook : list) {
            if (event instanceof CancellableEvent
                    && ((CancellableEvent) event).isCancelled()
                    && listenerHook.config.ignoreCanceled()) {
                continue;
            }
            listenerHook.call(event);
        }
    }

    private static class ListenerHook {
        public MethodHandle method;
        public EventHandler config;

        public ListenerHook(MethodHandle method, EventHandler config) {
            this.method = method;
            this.config = config;
        }

        public void call(Event event) {
            try {
                method.invoke(event);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
