module UberChop {
    requires kotlin.stdlib;
    requires BotWithUs.api;
    requires BotWithUs.imgui;
    requires BotWithUs.navigation.api;
    requires org.slf4j;
    requires com.google.gson;
    requires static xapi;
    requires static java.desktop;

    // Open for reflection-based serializers and framework annotations
    opens com.uberith.uberchop to BotWithUs.api, com.google.gson;
    // Declare service provider so ServiceLoader can discover this script on the module path
    provides net.botwithus.scripts.Script with com.uberith.uberchop.UberChop;
}
