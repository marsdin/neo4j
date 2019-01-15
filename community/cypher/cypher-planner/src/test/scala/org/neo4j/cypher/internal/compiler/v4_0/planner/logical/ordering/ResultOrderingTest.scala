/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v4_0.planner.logical.ordering

import org.neo4j.cypher.internal.compiler.v4_0.planner.LogicalPlanningTestSupport2
import org.neo4j.cypher.internal.ir.v4_0.{InterestingOrder, InterestingOrderCandidate, ProvidedOrder, RequiredOrderCandidate}
import org.neo4j.cypher.internal.planner.v4_0.spi.IndexOrderCapability
import org.neo4j.cypher.internal.planner.v4_0.spi.IndexOrderCapability.{ASC, BOTH, DESC, NONE}
import org.neo4j.cypher.internal.v4_0.util.symbols._
import org.neo4j.cypher.internal.v4_0.util.test_helpers.CypherFunSuite

class ResultOrderingTest extends CypherFunSuite with LogicalPlanningTestSupport2 {

  test("Empty required order results in provided order of index order capability ascending") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.empty, Seq("x" -> "foo"), Seq(CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo"))
  }

  test("Single property required DESC still results in provided ASC if index is not capable of DESC") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("x", "foo"))), Seq("x" -> "foo"), Seq(CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo"))
  }

  test("Single property required ASC results in provided DESC if index is not capable of ASC") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo"))), Seq("x" -> "foo"), Seq(CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo"))
  }

  test("Single property no capability results in empty provided order") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.empty, Seq("x" -> "foo"), Seq(CTInteger), capability(NONE)) should be(ProvidedOrder.empty)
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("y", "foo"))), Seq("x" -> "foo"), Seq(CTInteger), capability(NONE)) should be(ProvidedOrder.empty)
  }

  test("Single property required order results in matching provided order for compatible index capability") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo"))), Seq("x" -> "foo"), Seq(CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("x", "foo"))), Seq("x" -> "foo"), Seq(CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo"))
  }

  test("Single property required order with projected property results in matching provided order for compatible index capability") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(varFor("xfoo"), Map("xfoo" -> prop("x", "foo")))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(varFor("xfoo"), Map("xfoo" -> prop("x", "foo")))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.desc("x.foo"))
  }

  test("Single property required order with projected node results in matching provided order for compatible index capability") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("y", "foo"), Map("y" -> varFor("x")))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("y", "foo"), Map("y" -> varFor("x")))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.desc("x.foo"))
  }

  test("Multi property required order results in matching provided order for compatible index capability") {
    val properties = Seq("x", "y", "z").map(_ -> "foo")
    val interestingOrder = InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo")).asc(prop("z", "foo")))

    ResultOrdering.withIndexOrderCapability(interestingOrder, properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo").asc("z.foo"))
  }

  test("Multi property required order results in provided order if property order does not match") {
    val properties = Seq("y", "x", "z").map(_ -> "foo")
    val interestingOrder = InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo")).asc(prop("z", "foo")))

    ResultOrdering.withIndexOrderCapability(interestingOrder, properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("y.foo").asc("x.foo").asc("z.foo"))
  }

  test("Multi property required order results in provided order if property order partially matches") {
    val properties = Seq("x", "z", "y").map(_ -> "foo")
    val interestingOrder = InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo")).asc(prop("z", "foo")))

    ResultOrdering.withIndexOrderCapability(interestingOrder, properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("z.foo").asc("y.foo"))
  }

  test("Multi property required order results in provided order if mixed sort direction") {
    val properties = Seq("x", "y", "z", "w").map(_ -> "foo")
    val interestingOrder = InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo")).desc(prop("z", "foo")).asc(prop("w", "foo")))

    // Index can only give full ascending or descending, not a mixture. Therefore we follow the first required order
    ResultOrdering.withIndexOrderCapability(interestingOrder, properties, properties.map(_ => CTInteger), capability(BOTH)) should be(ProvidedOrder.asc("x.foo").asc("y.foo").asc("z.foo").asc("w.foo"))
  }

  test("Shorter multi property required order results in provided order") {
    val properties = Seq("x", "y", "z", "w").map(_ -> "foo")
    val interestingOrder = InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo")))

    ResultOrdering.withIndexOrderCapability(interestingOrder, properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo").asc("z.foo").asc("w.foo"))
  }

  test("Longer multi property required order results in partial matching provided order") {
    val properties = Seq("x", "y").map(_ -> "foo")
    val interestingOrder = InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo")).asc(prop("z", "foo")).asc(prop("w", "foo")))

    val capabilities: Seq[CypherType] => IndexOrderCapability = _ => ASC
    ResultOrdering.withIndexOrderCapability(interestingOrder, properties, properties.map(_ => CTInteger), capabilities) should be(ProvidedOrder.asc("x.foo").asc("y.foo"))
  }

  // Test the interesting part of the InterestingOrder

  test("Single property interesting order results in provided order when required can't be fulfilled or is empty") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo"))

    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("x", "foo"))).interested(InterestingOrderCandidate.asc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo"))
  }

  test("Single property interesting order with projection results in matching provided order for compatible index capability") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(varFor("xfoo"),
      Map("xfoo" -> prop("x", "foo")))), Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(varFor("xfoo"),
      Map("xfoo" -> prop("x", "foo")))), Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.desc("x.foo"))

    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("y", "foo"), Map("y" -> varFor("x")))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("y", "foo"), Map("y" -> varFor("x")))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(BOTH)) should be(ProvidedOrder.desc("x.foo"))
  }

  test("Single property capability results in default provided order when neither required nor interesting can be fulfilled or are empty") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo"))

    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("x", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo"))).interested(InterestingOrderCandidate.asc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo"))
  }

  test("Single property empty provided order when there is no capability") {
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(NONE)) should be(ProvidedOrder.empty)
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(NONE)) should be(ProvidedOrder.empty)

    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("x", "foo"))).interested(InterestingOrderCandidate.asc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(NONE)) should be(ProvidedOrder.empty)
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo"))),
      Seq("x" -> "foo"), Seq(CTInteger), capability(NONE)) should be(ProvidedOrder.empty)
  }

  test("Multi property interesting order results in provided order when required can't be fulfilled or is empty") {
    val properties = Seq("x", "y").map(_ -> "foo")

    // can't fulfill first interesting so falls back on second interesting
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))).interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo").desc("y.foo"))

    // can't fulfill required so falls back on interesting
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))).interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo").desc("y.foo"))
  }

  test("Multi property capability results in default provided order when neither required nor interesting can be fulfilled or are empty") {
    val properties = Seq("x", "y").map(_ -> "foo")

    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.required(RequiredOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))).interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo").desc("y.foo"))

    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo"))
    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo"))).interested(InterestingOrderCandidate.asc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(DESC)) should be(ProvidedOrder.desc("x.foo").desc("y.foo"))
  }

  test("Multi property empty provided order when there is no capability") {
    val properties = Seq("x", "y").map(_ -> "foo")

    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))).interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(NONE)) should be(ProvidedOrder.empty)

    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))).interested(InterestingOrderCandidate.desc(prop("x", "foo")).desc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(NONE)) should be(ProvidedOrder.empty)
  }

  test("Multi property interesting order results in provided order if mixed sort direction") {
    val properties = Seq("x", "y", "z").map(_ -> "foo")

    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.desc(prop("x", "foo")).asc(prop("y", "foo")).desc(prop("z", "foo"))),
      properties, properties.map(_ => CTInteger), capability(BOTH)) should be(ProvidedOrder.desc("x.foo").desc("y.foo").desc("z.foo"))
  }

  test("Shorter multi property interesting order results in provided order") {
    val properties = Seq("x", "y", "z", "w").map(_ -> "foo")

    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo"))),
      properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo").asc("z.foo").asc("w.foo"))
  }

  test("Longer multi property interesting order results in partial matching provided order") {
    val properties = Seq("x", "y").map(_ -> "foo")

    ResultOrdering.withIndexOrderCapability(InterestingOrder.interested(InterestingOrderCandidate.asc(prop("x", "foo")).asc(prop("y", "foo")).asc(prop("z", "foo")).asc(prop("w", "foo"))),
      properties, properties.map(_ => CTInteger), capability(ASC)) should be(ProvidedOrder.asc("x.foo").asc("y.foo"))
  }

  private def capability(capability: IndexOrderCapability): Seq[CypherType] => IndexOrderCapability = _ => capability
}
