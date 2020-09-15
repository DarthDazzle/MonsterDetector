package net.fabricmc.example;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public class Hooks {
    public static final Event<Runnable> DEBUG_RENDER_PRE = EventFactory.createArrayBacked(Runnable.class, listeners -> {
        return () -> {
            for (Runnable listener : listeners)
                listener.run();
        };
    });
    
}
