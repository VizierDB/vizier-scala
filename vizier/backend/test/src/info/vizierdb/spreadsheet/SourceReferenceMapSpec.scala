/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.spreadsheet

import org.specs2.mutable.Specification

class SourceReferenceMapSpec
  extends Specification
{

  "No Edits" >>
  {
    val map = new SourceReferenceMap()

    map(0) must beEqualTo(Some(0))
    map(10) must beEqualTo(Some(10))
    map(15) must beEqualTo(Some(15))
    map(20) must beEqualTo(Some(20))

  }

  "Simple Insertion" >>
  {
    val map = new SourceReferenceMap()

    map.insert(10, 5)

    map(0) must beEqualTo(Some(0))
    map(8) must beEqualTo(Some(8))
    map(9) must beEqualTo(Some(9))
    map(10) must beEqualTo(None)
    map(12) must beEqualTo(None)
    map(14) must beEqualTo(None)
    map(15) must beEqualTo(Some(10))
  }

  "Simple Deletion" >>
  {
    val map = new SourceReferenceMap()

    map.delete(10, 5)

    map(0) must beEqualTo(Some(0))
    map(8) must beEqualTo(Some(8))
    map(9) must beEqualTo(Some(9))
    map(10) must beEqualTo(Some(15))
    map(15) must beEqualTo(Some(20))
  }

  "A Few Insertions" >> 
  {
    val map = new SourceReferenceMap()
    
    map.insert(10, 5)
    map.insert(20, 5) // aka 15 before the insertion
    map.insert(5, 5)

    map(4) must beEqualTo(Some(4))
    map(5) must beEqualTo(None)
    map(9) must beEqualTo(None)
    map(10) must beEqualTo(Some(5))
    map(14) must beEqualTo(Some(9))
    map(15) must beEqualTo(None)
    map(19) must beEqualTo(None)
    map(20) must beEqualTo(Some(10))
    map(24) must beEqualTo(Some(14))
    map(25) must beEqualTo(None)
    map(29) must beEqualTo(None)
    map(30) must beEqualTo(Some(15))
  }

  "Overlapping Insertions" >> 
  {
    val map = new SourceReferenceMap()

    map.insert(10, 5)
    map.insert(15, 5)

    map(0) must beEqualTo(Some(0))
    map(9) must beEqualTo(Some(9))
    map(10) must beEqualTo(None)
    map(14) must beEqualTo(None)
    map(15) must beEqualTo(None)
    map(19) must beEqualTo(None)
    map(20) must beEqualTo(Some(10))
  }

  "Insert After Delete (Insert First)" >>
  {
    val map = new SourceReferenceMap()

    map.insert(10, 5)
      // 0-9 -> 0-9
      // 10-14 -> New
      // 15-20 -> 10-20
    map.delete(5, 5)
      // 0-4 -> 0-4
      // 5-9 -> New
      // 10-15 -> 10-15

    map(0) must beEqualTo(Some(0))
    map(4) must beEqualTo(Some(4))
    map(5) must beEqualTo(None)
    map(9) must beEqualTo(None)
    map(10) must beEqualTo(Some(10))
  }

  "Insert Before Delete (Insert First)" >>
  {
    val map = new SourceReferenceMap()

    map.insert(10, 5)
      // 0-9 -> 0-9
      // 10-14 -> New
      // 15-30 -> 10-25
    map.delete(15, 5)
      // 0-9 -> 0-9
      // 10-14 -> New
      // 15-30 -> 15-25

    map(0) must beEqualTo(Some(0))
    map(9) must beEqualTo(Some(9))
    map(10) must beEqualTo(None)
    map(14) must beEqualTo(None)
    map(15) must beEqualTo(Some(15))
  }

  "Insert Gets Deleted (Insert First)" >>
  {
    val map = new SourceReferenceMap()

    map.insert(10, 5)
      // 0-9 -> 0-9
      // 10-14 -> New
      // 15-30 -> 10-25
    map.delete(10, 5)
      // 0-9 -> 0-9
      // 10-14 -> New
      // 15-30 -> 15-25

    map(0) must beEqualTo(Some(0))
    map(9) must beEqualTo(Some(9))
    map(10) must beEqualTo(Some(10))
    map(15) must beEqualTo(Some(15))
  }

  "Move" >>
  {
    val map = new SourceReferenceMap()

    map.move(5l, 20l, 5)
      // 0-4 -> 0-4
      // 5-19 -> 10-24
      // 20-24 -> 5-9
      // 25+ -> id

    map(0) must beEqualTo(Some(0))
    map(4) must beEqualTo(Some(4))
    map(5) must beEqualTo(Some(10))
    map(19) must beEqualTo(Some(24))
    map(20) must beEqualTo(Some(5))
    map(24) must beEqualTo(Some(9))
    map(25) must beEqualTo(Some(25))
  }
}