module UberScriptAPI.main {
    requires kotlin.stdlib;
    requires BotWithUs.api;
    requires com.google.gson;
    requires static java.desktop;
    requires org.slf4j;
    requires xapi;

    exports com.uberith.api.utils;
    exports com.uberith.api.script;
    exports com.uberith.api.script.handlers;
    exports com.uberith.api.game.world;
    exports com.uberith.api.game.skills.magic;
    exports com.uberith.api.game.skills.firemaking;
    exports com.uberith.api.game.skills.fletching;
    exports com.uberith.api.ui;
    exports com.uberith.api.ui.imgui;
}
