module UberScriptAPI.main {
    requires kotlin.stdlib;
    requires BotWithUs.api;
    requires com.google.gson;
    requires static java.desktop;
    requires org.slf4j;

    exports com.uberith.api;
    exports com.uberith.api.utils;
    exports com.uberith.api.game;
    exports com.uberith.api.game.world;
    exports com.uberith.api.game.inventories;
    exports com.uberith.api.game.items;
    exports com.uberith.api.game.skills.magic;
    exports com.uberith.api.game.skills.woodcutting;
    exports com.uberith.api.ui;
}
