package testplugin;

import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import rx.Observable;
import rx.bukkit.RxJavaPlugin;
import rx.bukkit.observable.EventObservable;
import rx.bukkit.observable.CommandEvent;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Copyright 2014 Ryan Michela
 */
public class Plugin extends JavaPlugin {
    @Override
    public void onLoad() {
        RxJavaPlugin.initializeRx(this);
    }

    @Override
    public void onEnable() {
        Observable<PlayerInteractEvent> eveObs = EventObservable.forEvent(PlayerInteractEvent.class, this);

        eveObs
            .buffer(1, TimeUnit.SECONDS)
            .filter(playerInteractEvents -> playerInteractEvents.size() > 2)
            .map(new Func1<List<PlayerInteractEvent>, String>() {
                @Override
                public String call(List<PlayerInteractEvent> playerInteractEvents) {
                    return playerInteractEvents.size() + " clicks";
                }
            })
//            .observeOn(BukkitRxScheduler.forPlugin(this))
            .subscribe(s -> {
                getLogger().info(Thread.currentThread().getId() + " " + s);
            });

        eveObs
            .subscribe(playerInteractEvent -> {
                getLogger().info(Thread.currentThread().getId() + "");
            });

        Observable<CommandEvent> cmdObs = EventObservable.fromBukkitCommand(this, "cmd");
        cmdObs.subscribe(commandEvent -> {
            getLogger().info(commandEvent.getCommand() + " " + commandEvent.getArgs().length);
        });

        cmdObs.subscribe(commandEvent -> {
            getLogger().info(commandEvent.isCancelled() + "");
        });
    }
}
