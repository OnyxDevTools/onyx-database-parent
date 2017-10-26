package embedded.relationship

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.*
import embedded.base.BaseTest
import entities.AddressNoPartition
import entities.PersonNoPartition
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.Arrays

/**
 * Created by Tim Osborn on 3/17/17.
 */
@Category(EmbeddedDatabaseTests::class)
class RelationshipSelectTest : BaseTest() {

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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
    fun testDistinctValues() {
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
        query.isDistinct = true
        query.selections = Arrays.asList("firstName")
        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size == 2)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
        assert(addresses[0]["address"] is Map<*, *>)
        assert((addresses[0]["address"] as Map<*, *>)["street"] == "Sluisvaart")

    }

    @Test
    @Throws(OnyxException::class)
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(AddressNoPartition::class.java, first.and(second))
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size > 0)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] != null)
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] != null)
    }

    @Test
    @Throws(OnyxException::class)
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(AddressNoPartition::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size > 0)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] != null)
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] != null)
    }


    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }


    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
    }

    @Test
    @Throws(OnyxException::class)
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
        assert(addresses.size > 0)
        assert(addresses[0]["address"] is Map<*, *>)
        assert((addresses[0]["address"] as Map<*, *>)["street"] == "Sluisvaart")

    }

    @Test
    @Throws(OnyxException::class)
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val second = QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob")
        val query = Query(AddressNoPartition::class.java, first.and(second))
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

            find(person2.address!!)
        }


        val first = QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart")
        val query = Query(AddressNoPartition::class.java, first)
        query.selections = Arrays.asList("id", "street", "occupants")
        query.queryOrders = Arrays.asList(QueryOrder("street"))

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size > 0)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
    }
}
