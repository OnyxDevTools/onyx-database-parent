package embedded.relationship

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.*
import entities.Address
import entities.Person
import embedded.base.BaseTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.Arrays

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Category(EmbeddedDatabaseTests::class)
class RelationshipPartitionSelectTest : BaseTest() {
    @Before
    @Throws(OnyxException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        query.partition = QueryPartitionMode.ALL
        val addresses = manager.executeQuery<Person>(query)
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
        assert(addresses[0]["address"] is Map<*, *>)
        assert((addresses[0]["address"] as Map<*, *>)["street"] == "Sluisvaart")

    }

    @Test
    @Throws(OnyxException::class)
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(Address::class.java, first.and(second))
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size > 0)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] is String)
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] is String)
    }

    @Test
    @Throws(OnyxException::class)
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Address::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size > 0)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] == "Cristian")
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] == "Timbob")
    }


    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }


    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
        assert(addresses[0]["address"] is Map<*, *>)
        assert((addresses[0]["address"] as Map<*, *>)["street"] == "Sluisvaart")

    }

    @Test
    @Throws(OnyxException::class)
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(Address::class.java, first.and(second))
        query.selections = Arrays.asList("id", "street", "occupants")
        query.queryOrders = Arrays.asList(QueryOrder("occupants.firstName"))

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size > 0)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] == "Timbob")
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] == "Cristian")
    }

    @Test
    @Throws(OnyxException::class)
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(Address::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")
        query.queryOrders = Arrays.asList(QueryOrder("street"))

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size > 0)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
    }
}
