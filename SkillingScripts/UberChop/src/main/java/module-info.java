module UberChop {
    requires kotlin.stdlib;
    requires BotWithUs.api;
    requires BotWithUs.imgui;
    requires BotWithUs.navigation.api;
    requires kotlinx.coroutines.core;
    requires org.slf4j;
    requires com.google.gson;
    requires static xapi;
    requires static java.desktop;

    opens com.uberith.uberchop to BotWithUs.api, com.google.gson;
    opens com.uberith.api.script.handlers to com.google.gson;
    opens com.uberith.api.script to com.google.gson;
    provides net.botwithus.scripts.Script with com.uberith.uberchop.UberChop;
}

