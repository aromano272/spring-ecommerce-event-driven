package com.aromano.ecommerce.admindashboard

import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@EnableAdminServer
@SpringBootApplication
class AdminDashboardApp

fun main(args: Array<String>) {
    runApplication<AdminDashboardApp>(*args)
}