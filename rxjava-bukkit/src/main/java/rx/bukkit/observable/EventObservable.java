package rx.bukkit.observable;

import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.subscriptions.Subscriptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Copyright 2014 Ryan Michela
 */
public enum EventObservable { ;
    public static <T extends PlayerEvent> Observable.Transformer<T, Player> playerAlone() {
        return obs -> obs.map(T::getPlayer);
    }

    @Setter private static Plugin currentPlugin;
    @Setter private static Scheduler bukkitScheduler;

    public static <T extends Event> Observable<T> forEvent(Class<T> eventClass) {
        return forEvent(eventClass, EventPriority.NORMAL);
    }

    public static <T extends Event> Observable<T> forEvent(Class<T> eventClass, EventPriority priority) {
        return forEvent(eventClass, priority, false);
    }

    public static <T extends Event> Observable<T> forEvent(Class<T> eventClass, EventPriority priority, boolean ignoreCancelled) {
        if (currentPlugin == null)
            throw new IllegalStateException("The default plugin has not been set!");

        return forEvent(eventClass, currentPlugin, priority, ignoreCancelled);
    }

    public static <T extends Event> Observable<T> forEvent(Class<T> eventClass, Plugin plugin) {
        return forEvent(eventClass, plugin, EventPriority.NORMAL);
    }

    public static <T extends Event> Observable<T> forEvent(Class<T> eventClass, Plugin plugin, EventPriority priority) {
        return forEvent(eventClass, plugin, priority, false);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Event> Observable<T> forEvent(Class<T> eventClass, Plugin plugin, EventPriority priority, boolean ignoreCancelled) {
        return forEvents(plugin, priority, ignoreCancelled, new Class[]{eventClass});
    }

    @SafeVarargs
    public static <T extends Event> Observable<T> forEvent(Class<? extends T>... eventClasses) {
        return forEvents(currentPlugin, EventPriority.NORMAL, false, eventClasses);
    }


    private static <T extends Event> Observable<T> forEvents(final Plugin plugin, final EventPriority priority, final boolean ignoreCancelled, final Class<? extends T>[] eventClasses) {
        return Observable.create(new Observable.OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                PluginManager pluginManager = plugin.getServer().getPluginManager();

                final Listener listener = new Listener() {
                };

                for (Class<? extends T> eventClass : eventClasses)
                    pluginManager.registerEvent(eventClass, listener, priority, (l, event) -> subscriber.onNext((T) event), plugin, ignoreCancelled);

                // Unregister the event handler on un-subscription
                subscriber.add(Subscriptions.create(() -> HandlerList.unregisterAll(listener)));

                //and cleanup when the plugin disables
                pluginManager.registerEvent(PluginDisableEvent.class, listener, EventPriority.NORMAL, (listener1, event) -> {
                    if (((PluginDisableEvent) event).getPlugin() == plugin) {
                        subscriber.onCompleted();
                    }
                }, plugin, false);
            }
        }).observeOn(bukkitScheduler);
    }

    private static Map<String, MultiplexingCommandExecutor> establishedExecutors = new HashMap<>();

    public static Observable<CommandEvent> fromBukkitCommand(final JavaPlugin plugin, final String command) {
        return Observable.create(new Observable.OnSubscribe<CommandEvent>() {
            @Override
            public void call(final Subscriber<? super CommandEvent> subscriber) {
                if (!establishedExecutors.containsKey(command)) {
                    MultiplexingCommandExecutor executor = new MultiplexingCommandExecutor();
                    plugin.getCommand(command).setExecutor(executor);
                    establishedExecutors.put(command, executor);
                }

                establishedExecutors.get(command).AddSubscriber(subscriber);
                registerCompletionOnDisable(subscriber, plugin, new Listener() {
                });
            }
        });
    }

    private static void registerCompletionOnDisable(final Subscriber subscriber, final Plugin plugin, Listener listener) {
        EventExecutor disableExecutor = (listener1, event) -> {
            if (((PluginDisableEvent) event).getPlugin() == plugin) {
                subscriber.onCompleted();
            }
        };
        plugin.getServer().getPluginManager().registerEvent(PluginDisableEvent.class, listener, EventPriority.NORMAL, disableExecutor, plugin, false);
    }
}
