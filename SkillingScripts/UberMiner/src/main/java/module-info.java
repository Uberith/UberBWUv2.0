module UberMiner {
    requires kotlin.stdlib;
    requires BotWithUs.api;
    requires kotlinx.coroutines.core;
    requires org.slf4j;
    requires static xapi;
    requires static java.desktop;

    opens com.uberith.uberminer to BotWithUs.api;

    provides net.botwithus.scripts.Script with com.uberith.uberminer.UberMiner;
}
