package database.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.AddressNoPartition
import entities.AllAttributeV2Entity
import entities.PersonNoPartition
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * Created by Tim Osborn on 3/17/17.
 */
@RunWith(Parameterized::class)
class RelationshipSelectTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testInvalidQueryException() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val query = Query(PersonNoPartition::class.java, QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart"))
        val addresses = manager.executeQuery<PersonNoPartition>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
    }

    @Test
    fun testDistinctValues() {
        for (i in 0..49) {
            var person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            person = PersonNoPartition()
            person.firstName = "Timbob"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val query = Query(PersonNoPartition::class.java, QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart"))
        query.isDistinct = true
        query.selections = Arrays.asList("firstName")
        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertEquals(2, addresses.size, "Missing query data")
    }

    @Test
    fun testQuerySpecificPartition() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(PersonNoPartition::class.java, first.and(second))
        val addresses = manager.executeQuery<PersonNoPartition>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
    }

    @Test
    fun testSelectAttribute() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(PersonNoPartition::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address.street")
        val addresses = manager.executeQuery<PersonNoPartition>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
    }

    @Test
    fun testSelectRelationship() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(PersonNoPartition::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address")
        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
        assertTrue(addresses[0]["address"] is AddressNoPartition)
        assertEquals("Sluisvaart", (addresses[0]["address"] as AddressNoPartition).street, "Incorrect Query Data")
    }

    @Test
    fun testToManySelectRelationship() {
        manager.executeDelete(Query(PersonNoPartition::class.java))
        manager.executeDelete(Query(AddressNoPartition::class.java))
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = PersonNoPartition()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = AddressNoPartition()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(AddressNoPartition::class.java, first.and(second))
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is PersonNoPartition)
        assertNotNull(((addresses[0]["occupants"] as List<*>)[0] as PersonNoPartition).firstName)
        assertNotNull(((addresses[0]["occupants"] as List<*>)[1] as PersonNoPartition).firstName)
    }

    @Test
    fun testToManySelectRelationshipNoRelationshipCriteria() {
        manager.executeDelete(Query(PersonNoPartition::class.java))
        manager.executeDelete(Query(AddressNoPartition::class.java))
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = PersonNoPartition()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = AddressNoPartition()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(AddressNoPartition::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is PersonNoPartition)
        assertNotNull(((addresses[0]["occupants"] as List<*>)[0] as PersonNoPartition).firstName)
        assertNotNull(((addresses[0]["occupants"] as List<*>)[1] as PersonNoPartition).firstName)
    }

    @Test
    fun testQuerySpecificPartitionOrderBy() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(PersonNoPartition::class.java, first.and(second))
        query.queryOrders = Arrays.asList(QueryOrder("firstName"), QueryOrder("address.street"))
        val addresses = manager.executeQuery<PersonNoPartition>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
    }


    @Test
    fun testSelectAttributeOrderBy() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(PersonNoPartition::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address.street")
        query.queryOrders = Arrays.asList(QueryOrder("firstName"), QueryOrder("address.street"))
        val addresses = manager.executeQuery<PersonNoPartition>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
    }

    @Test
    fun testSelectRelationshipOrderBy() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(PersonNoPartition::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address")
        query.queryOrders = Arrays.asList(QueryOrder("firstName"), QueryOrder("address.street"))
        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
        assertTrue(addresses[0]["address"] is AddressNoPartition)
        assertEquals("Sluisvaart", (addresses[0]["address"] as AddressNoPartition).street)

    }

    @Test
    fun testToManySelectRelationshipOrderBy() {
        manager.executeDelete(Query(PersonNoPartition::class.java))
        manager.executeDelete(Query(AddressNoPartition::class.java))

        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = PersonNoPartition()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = AddressNoPartition()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(AddressNoPartition::class.java, first.and(second))
        query.selections = Arrays.asList("id", "street", "occupants")
        query.queryOrders = Arrays.asList(QueryOrder("occupants.firstName"))

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is PersonNoPartition)
    }

    @Test
    fun testToManySelectRelationshipNoRelationshipCriteriaOrderBy() {
        for (i in 0..49) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = PersonNoPartition()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = AddressNoPartition()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(AddressNoPartition::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")
        query.queryOrders = Arrays.asList(QueryOrder("street"))

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Missing query data")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is PersonNoPartition)
    }

    @Test
    fun testEmbeddedObject() {
        val entity = AllAttributeV2Entity()

        entity.id = "A"
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.stringValue = "String key"
        entity.dateValue = Date(1483736263743L)
        entity.doublePrimitive = 342.23
        entity.doubleValue = 232.2
        entity.booleanPrimitive = true
        entity.booleanValue = false
        entity.mutableFloat = 23.234f
        entity.floatValue = 666.3453f
        entity.mutableByte = 5.toByte()
        entity.byteValue = 7.toByte()
        entity.mutableShort = 65
        entity.shortValue = 44
        entity.mutableChar = 'C'
        entity.charValue = 'D'
        entity.entity = AllAttributeV2Entity()
        entity.entity?.shortValue = 49
        entity.aMutableBytes = arrayOf(2.toByte(), 8.toByte(), 7.toByte())
        entity.bytes = byteArrayOf(4.toByte(), 4.toByte(), 2.toByte())
        entity.shorts = shortArrayOf(4, 4, 2)
        entity.mutableShorts = arrayOf(4, 4, 2)
        entity.strings = arrayOf("A", "V")
        entity.operator = QueryCriteriaOperator.CONTAINS
        entity.entitySet = HashSet()
        entity.entitySet?.add(entity.entity!!)
        entity.entityList = ArrayList()
        entity.entityList?.add(entity.entity!!)

        manager.saveEntity<IManagedEntity>(entity)

        val results = manager.from<AllAttributeV2Entity>().where("entity.shortValue" eq 49).list<AllAttributeV2Entity>()
        assertEquals(1, results.size)
    }

    @Test
    fun testEmbeddedListObject() {
        val entity = AllAttributeV2Entity()

        entity.id = "A"
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.stringValue = "String key"
        entity.dateValue = Date(1483736263743L)
        entity.doublePrimitive = 342.23
        entity.doubleValue = 232.2
        entity.booleanPrimitive = true
        entity.booleanValue = false
        entity.mutableFloat = 23.234f
        entity.floatValue = 666.3453f
        entity.mutableByte = 5.toByte()
        entity.byteValue = 7.toByte()
        entity.mutableShort = 65
        entity.shortValue = 44
        entity.mutableChar = 'C'
        entity.charValue = 'D'
        entity.entity = AllAttributeV2Entity()
        entity.entity?.shortValue = 49
        entity.aMutableBytes = arrayOf(2.toByte(), 8.toByte(), 7.toByte())
        entity.bytes = byteArrayOf(4.toByte(), 4.toByte(), 2.toByte())
        entity.shorts = shortArrayOf(4, 4, 2)
        entity.mutableShorts = arrayOf(4, 4, 2)
        entity.strings = arrayOf("A", "V")
        entity.operator = QueryCriteriaOperator.CONTAINS
        entity.entitySet = HashSet()
        entity.entitySet?.add(entity.entity!!)
        entity.entityList = ArrayList()
        entity.entityList?.add(entity.entity!!)

        manager.saveEntity<IManagedEntity>(entity)

        val results = manager.from<AllAttributeV2Entity>().where("entityList.any.shortValue" eq 49).list<AllAttributeV2Entity>()
        assertEquals(1, results.size)
    }

    @Test
    fun testSelectRelationshipValuesWithNormal() {
        for (i in 0..3) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = i
            manager.saveEntity<IManagedEntity>(person)
        }

        var results = manager.from(PersonNoPartition::class)
                .where("address.houseNr" eq 2).and("firstName" neq "Cristian")
                .list<PersonNoPartition>()

        assertEquals(0, results.size, "Expected 0 results")

        results = manager.from(PersonNoPartition::class)
                .where("address.houseNr" eq 2).and("firstName" eq "Cristian")
                .list()

        assertEquals(1, results.size, "Expected 1 results")

        for (i in 0..3) {
            val person = PersonNoPartition()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = AddressNoPartition()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = i
            manager.saveEntity<IManagedEntity>(person)
        }

        results = manager.from(PersonNoPartition::class)
                .where("address.houseNr" eq 2).and("firstName" neq "Cristian")
                .list<PersonNoPartition>()

        assertEquals(0, results.size, "Expected 0 results")

        results = manager.from(PersonNoPartition::class)
                .where("address.houseNr" eq 2).and("firstName" eq "Cristian")
                .list()

        assertEquals(2, results.size, "Expected 2 results")
    }
}
