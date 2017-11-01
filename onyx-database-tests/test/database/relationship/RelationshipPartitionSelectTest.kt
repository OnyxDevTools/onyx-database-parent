package database.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.Address
import entities.Person
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class RelationshipPartitionSelectTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testInvalidQueryException() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val query = Query(Person::class.java, QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart"))
        query.partition = "ASDF"
        val addresses = manager.executeQuery<Person>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
    }

    @Test
    fun testInsert() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Person::class.java, first.and(second))
        val addresses = manager.executeQuery<Person>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
    }

    @Test
    fun testQuerySpecificPartition() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Person::class.java, first.and(second))
        query.partition = "ASDF"
        val addresses = manager.executeQuery<Person>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
    }

    @Test
    fun testSelectAttribute() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Person::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address.street")
        query.partition = "ASDF"
        val addresses = manager.executeQuery<Person>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
    }

    @Test
    fun testSelectRelationship() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Person::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address")
        query.partition = "ASDF"
        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
        assertTrue(addresses[0]["address"] is Map<*, *>, "Result format should be a map")
        assertEquals("Sluisvaart", (addresses[0]["address"] as Map<*, *>)["street"], "Addresses does not have correct data")
    }

    @Test
    fun testToManySelectRelationship() {
        manager.executeDelete(Query(Person::class.java))
        manager.executeDelete(Query(Address::class.java))

        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = Person()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = Address()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(Address::class.java, first.and(second))
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assertTrue(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] is String)
        assertTrue(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] is String)
    }

    @Test
    fun testToManySelectRelationshipNoRelationshipCriteria() {
        manager.executeDelete(Query(Person::class.java))
        manager.executeDelete(Query(Address::class.java))

        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = Person()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = Address()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Address::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assertTrue(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] == "Cristian")
        assertTrue(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] == "Timbob")
    }

    @Test
    fun testQuerySpecificPartitionOrderBy() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Person::class.java, first.and(second))
        query.queryOrders = Arrays.asList(QueryOrder("firstName"), QueryOrder("address.street"))
        query.partition = "ASDF"
        val addresses = manager.executeQuery<Person>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
    }


    @Test
    fun testSelectAttributeOrderBy() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Person::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address.street")
        query.partition = "ASDF"
        query.queryOrders = Arrays.asList(QueryOrder("firstName"), QueryOrder("address.street"))
        val addresses = manager.executeQuery<Person>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
    }

    @Test
    fun testSelectRelationshipOrderBy() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)
        }

        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian")
        val second = QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Person::class.java, first.and(second))
        query.selections = Arrays.asList("firstName", "address")
        query.partition = "ASDF"
        query.queryOrders = Arrays.asList(QueryOrder("firstName"), QueryOrder("address.street"))
        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
        assertTrue(addresses[0]["address"] is Map<*, *>)
        assertTrue((addresses[0]["address"] as Map<*, *>)["street"] == "Sluisvaart")

    }

    @Test
    fun testToManySelectRelationshipOrderBy() {
        manager.executeDelete(Query(Person::class.java))
        manager.executeDelete(Query(Address::class.java))

        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = Person()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = Address()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(Address::class.java, first.and(second))
        query.selections = Arrays.asList("id", "street", "occupants")
        query.queryOrders = Arrays.asList(QueryOrder("occupants.firstName")) // Has no effect but want to ensure, it does not break anything

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
    }

    @Test
    fun testToManySelectRelationshipNoRelationshipCriteriaOrderBy() {
        for (i in 0..49) {
            val person = Person()
            person.firstName = "Cristian"
            person.lastName = "Vogel" + i
            person.address = Address()
            person.address!!.street = "Sluisvaart"
            person.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person)

            val person2 = Person()
            person2.firstName = "Timbob"
            person2.lastName = "Rooski" + i
            person2.address = Address()
            person2.address!!.id = person.address!!.id
            person2.address!!.street = "Sluisvaart"
            person2.address!!.houseNr = 98
            manager.saveEntity<IManagedEntity>(person2)

            manager.find<IManagedEntity>(person2.address!!)
        }

        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Address::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")
        query.queryOrders = Arrays.asList(QueryOrder("street"))

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assertTrue(addresses.isNotEmpty(), "Query missing results")
        assertTrue(addresses[0]["occupants"] is List<*>)
        assertTrue((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
    }
}
