module UberChop.main {
    requires kotlin.stdlib;
    requires static UberScriptAPI.main; // compile-time only; shaded into this jar
    requires BotWithUs.api;
    requires BotWithUs.imgui;
    requires BotWithUs.navigation.api;
    requires org.slf4j;
    requires static xapi;
    requires com.google.gson;
    requires static java.desktop;

    opens com.uberith.uberchop to BotWithUs.api; // needed for event annotations

    provides net.botwithus.scripts.Script with com.uberith.uberchop.UberChop;
}
