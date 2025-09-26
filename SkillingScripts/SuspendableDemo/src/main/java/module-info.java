module SuspendableDemo {
    requires kotlin.stdlib;
    requires BotWithUs.api;
    requires BotWithUs.imgui;
    requires kotlinx.coroutines.core;
    requires org.slf4j;
    requires static xapi;
    requires static java.desktop;

    opens com.uberith.suspendabledemo to BotWithUs.api;

    provides net.botwithus.scripts.Script with com.uberith.suspendabledemo.SuspendableDemo;
}
