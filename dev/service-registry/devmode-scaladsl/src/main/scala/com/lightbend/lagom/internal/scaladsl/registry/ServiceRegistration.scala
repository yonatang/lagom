/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.registry

import java.net.URI

import com.lightbend.lagom.scaladsl.api.ServiceInfo
import com.typesafe.config.Config
import play.api.inject.ApplicationLifecycle
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class ServiceRegistration(serviceInfo: ServiceInfo, lifecycle: ApplicationLifecycle, config: Config,
                          registry: ServiceRegistry)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(this.getClass)
  private val uri = {
    val httpAddress = config.getString("play.server.http.address")
    val httpPort = config.getString("play.server.http.port")
    URI.create(s"http://$httpAddress:$httpPort")
  }

  lifecycle.addStopHook { () =>
    Future.sequence(serviceInfo.locatableServices.map {
      case (service, _) => registry.unregister(service).invoke()
    }).map(_ => ())
  }

  serviceInfo.locatableServices.foreach {
    case (service, acls) =>
      registry.register(service)
        .invoke(ServiceRegistryService(uri, acls))
        .onComplete {
          case Success(_) =>
            logger.debug(s"Service name=[$service] successfully registered with service locator.")
          case Failure(e) =>
            logger.error(s"Service name=[$service}] couldn't register itself to the service locator.", e)
        }
  }

}
