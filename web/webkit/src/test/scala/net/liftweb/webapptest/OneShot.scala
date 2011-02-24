/*
 * Copyright 2010-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.liftweb
package webapptest

import org.specs.Specification

import util._
import http._
import testing._
import Helpers._

import snippet.Counter


object OneShot extends Specification with RequestKit {
  doBeforeSpec(JettyTestServer.start())
  doAfterSpec(JettyTestServer.stop())

  def baseUrl = JettyTestServer.baseUrl


  "ContainerVars" should {
    "have correct int default" in {
      val tmp = LiftRules.sessionCreator
      try {
        LiftRules.sessionCreator = LiftRules.sessionCreatorForMigratorySessions
        
        val bx = 
          for {
            resp <- get("/cv_int")
            xml <- resp.xml
          } yield xml
        
        bx.open_! must ==/ (<int>45</int>)
      } finally {
        LiftRules.sessionCreator = tmp
      }
    }

    "be settable as Int" in {
      val tmp = LiftRules.sessionCreator
      try {
        LiftRules.sessionCreator = LiftRules.sessionCreatorForMigratorySessions
        
      val bx = 
        for {
          resp <- get("/cv_int/33")
          resp2 <- resp.get("/cv_int")
          xml <- resp2.xml
        } yield xml

      bx.open_! must ==/ (<int>33</int>)
      } finally {
        LiftRules.sessionCreator = tmp
      }
    }

    "be session aware" in {
      val tmp = LiftRules.sessionCreator
      try {
        LiftRules.sessionCreator = LiftRules.sessionCreatorForMigratorySessions
        
      val bx = 
        for {
          resp <- get("/cv_int/33")
          resp2 <- resp.get("/cv_int")
          xml <- resp2.xml
          resp3 <- get("/cv_int")
          xml2 <- resp3.xml
        } yield (xml, xml2)

      bx.open_!._1 must ==/ (<int>33</int>)
      bx.open_!._2 must ==/ (<int>45</int>)
      } finally {
        LiftRules.sessionCreator = tmp
      }
    }

    "support multiple vars" in {
      val tmp = LiftRules.sessionCreator
      try {
        LiftRules.sessionCreator = LiftRules.sessionCreatorForMigratorySessions
        
        val bx = 
          for {
            resp <- get("/cv_int/33")
            resp2 <- resp.get("/cv_int")
            xml <- resp2.xml
            respx <- resp.get("/cv_str/meow")
            resp3 <- resp.get("/cv_str")
            xml <- resp2.xml
            xml2 <- resp3.xml
          } yield (xml, xml2)
            
            bx.open_!._1 must ==/ (<int>33</int>)
              bx.open_!._2 must ==/ (<str>meow</str>)
      } finally {
        LiftRules.sessionCreator = tmp
      }
    }
  }

  "OneShot" should {
    "fire once for oneshot" >> {
      Counter.x = 0

      for {
        resp <- get("/oneshot")
        xml <- resp.xml
        span <- (xml \\ "span").filter(x => (x \ "@id").text == "one")
        in <- (span \\ "input")
        name <- in \ "@name"
      } {

        resp.get("/oneshot?"+urlEncode(name.text)+"=3")
        resp.get("/oneshot?"+urlEncode(name.text)+"=3")
      }

      Counter.x must_== 1
    }

    "fire multiple times for normal" >> {
      Counter.x = 0

      for {
        resp <- get("/oneshot")
        xml <- resp.xml
        span <- (xml \\ "span").filter(x => (x \ "@id").text == "two")
        in <- (span \\ "input")
        name <- in \ "@name"
      } {
        resp.get("/oneshot?"+urlEncode(name.text)+"=3")
        resp.get("/oneshot?"+urlEncode(name.text)+"=3")
      }


      Counter.x must_== 2
    }
  }

}
