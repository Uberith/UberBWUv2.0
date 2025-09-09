module UberScriptAPI.main {
    requires kotlin.stdlib;
    requires BotWithUs.api;
    requires static xapi;
    requires com.google.gson;
    requires java.desktop;

    exports com.uberith.api;
    exports com.uberith.api.utils;
    exports com.uberith.api.game;
    exports com.uberith.api.game.world;
    exports com.uberith.api.game.inventories;
    exports com.uberith.api.game.items;
    exports com.uberith.api.game.skills.magic;
}
