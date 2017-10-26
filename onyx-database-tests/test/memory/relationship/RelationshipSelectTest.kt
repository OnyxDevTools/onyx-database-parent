package memory.relationship

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
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
@Category(InMemoryDatabaseTests::class)
class RelationshipSelectTest : memory.base.BaseTest() {

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
        assert(addresses.size == 50)
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
        assert(addresses.size == 50)
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
        assert(addresses.size == 50)
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
        assert(addresses.size == 50)
        assert(addresses[0]["address"] is Map<*, *>)
        assert((addresses[0]["address"] as Map<*, *>)["street"] == "Sluisvaart")

    }

    @Test
    @Throws(OnyxException::class)
    fun testToManySelectRelationship() {
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
        assert(addresses.size == 50)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] != null)
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] != null)
    }

    @Test
    @Throws(OnyxException::class)
    fun testToManySelectRelationshipNoRelationshipCriteria() {
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
        assert(addresses.size == 50)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] != null)
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] != null)
    }

    @Test
    @Throws(OnyxException::class)
    fun testsToManySelectAttribute() {
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
        query.selections = Arrays.asList("id", "street", "occupants.firstName")

        val addresses = manager.executeQuery<Map<*, *>>(query)
        assert(addresses.size == 50)
        assert(addresses[0]["occupants.firstName"] is List<*>)
        assert((addresses[0]["occupants.firstName"] as List<*>)[0] is String)
    }


    @Test
    @Throws(OnyxException::class)
    fun testsToOneSelectAttribute() {
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


        val first = QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Timbob")
        val secnd = QueryCriteria("address.street", QueryCriteriaOperator.NOT_EQUAL, "Timbob")
        val query = Query(PersonNoPartition::class.java, first)
        query.selections = Arrays.asList("id", "firstName", "address.street")

        val persons = manager.executeQuery<Map<*, *>>(query)
        assert(persons.size == 50)
        assert(persons[0]["address.street"] is String)
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
        assert(addresses.size == 50)
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
        assert(addresses.size == 50)
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
        assert(addresses.size == 50)
        assert(addresses[0]["address"] is Map<*, *>)
        assert((addresses[0]["address"] as Map<*, *>)["street"] == "Sluisvaart")

    }

    @Test
    @Throws(OnyxException::class)
    fun testToManySelectRelationshipOrderBy() {
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
        assert(addresses.size == 50)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
        assert(((addresses[0]["occupants"] as List<*>)[1] as Map<*, *>)["firstName"] != null)
        assert(((addresses[0]["occupants"] as List<*>)[0] as Map<*, *>)["firstName"] != null)
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
        assert(addresses.size == 50)
        assert(addresses[0]["occupants"] is List<*>)
        assert((addresses[0]["occupants"] as List<*>)[0] is Map<*, *>)
    }

    @Test
    @Throws(OnyxException::class)
    fun testCascadeToManyBug() {

        val person = PersonNoPartition()
        person.firstName = "Cristian"
        person.lastName = "Vogel"

        manager.saveEntity<IManagedEntity>(person)

        val person2 = PersonNoPartition()
        person2.firstName = "Timbob"
        person2.lastName = "Rooski"

        manager.saveEntity<IManagedEntity>(person2)

        val addressNoPartition = AddressNoPartition()
        addressNoPartition.street = "ASDF"
        addressNoPartition.occupants = Arrays.asList(person, person2)

        manager.saveEntity<IManagedEntity>(addressNoPartition)

        val results = manager.list<AddressNoPartition>(AddressNoPartition::class.java)
        val iterator = results[0].occupants!!.iterator()
        while (iterator.hasNext()) {
            assert(iterator.next().address!!.id === addressNoPartition.id)
        }
        assert(results[0].occupants!!.size == 2)


    }

}
