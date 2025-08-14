rootProject.name = "ecommerce-event-sourcing"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(
    "accounting",
    "admindashboard",
    "common",
    "customer",
    "email",
    "inventory",
    "order",
    "supplier",

    "ingester",
    "transformer",
    "dispatcher",
)