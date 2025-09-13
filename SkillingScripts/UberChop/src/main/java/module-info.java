module UberChop {
    requires kotlin.stdlib;
    requires static UberScriptAPI.main; // compile-time only; shaded into this jar
    requires BotWithUs.api;
    requires BotWithUs.imgui;
    requires BotWithUs.navigation.api;
    requires org.slf4j;
    requires static xapi;
    requires static java.desktop;

    // Keep open to BotWithUs.api for event annotations
    opens com.uberith.uberchop to BotWithUs.api;
    // Declare service provider so ServiceLoader can discover this script on the module path
    provides net.botwithus.scripts.Script with com.uberith.uberchop.UberChop;
}
