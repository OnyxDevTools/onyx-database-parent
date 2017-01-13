package com.onyxdevtools.index;

import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.impl.CacheManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.index.entities.Book;

import java.io.IOException;
import java.util.List;

public class Main extends AbstractDemo
{

    public static void main(String[] args) throws IOException
    {

        //Initialize the database and get a handle on the PersistenceManager
        CacheManagerFactory factory = new CacheManagerFactory();
        factory.initialize();
        PersistenceManager manager = factory.getPersistenceManager();

        // Seed data with book test data
        seedData(manager);

        // Create a query to find children's books
        // Note: This query has been optimized since the Book#genre attribute is indexed.
        QueryCriteria childrenBookCriteria = new QueryCriteria("genre", QueryCriteriaOperator.EQUAL, "CHILDREN");
        Query findBooksByGenreQuery = new Query(Book.class, childrenBookCriteria);

        List<Book> childrenBooks = manager.executeQuery(findBooksByGenreQuery);
        assertEquals("There should be 3 children's books", childrenBooks.size(), 3);

        factory.close();
    }

    /**
     * Insert test data into a test database
     *
     * @param manager Persistence Manager used to insert data
     */
    protected static void seedData(PersistenceManager manager) throws EntityException
    {
        // Create test data
        Book harryPotter = new Book();
        harryPotter.setTitle("Harry Potter, Deathly Hallows");
        harryPotter.setDescription("Story about a kid that has abnormal creepy powers that seeks revenge on a poor innocent guy named Voldomort.");
        harryPotter.setGenre("CHILDREN");

        Book theGiver = new Book();
        theGiver.setTitle("The Giver");
        theGiver.setDescription("Something about a whole community of color blind people.");
        theGiver.setGenre("CHILDREN");

        Book twilight = new Book();
        twilight.setTitle("Twilight");
        twilight.setGenre("CHILDREN");
        twilight.setDescription("Book that lead to awful teenie bopper vampire movie.");

        Book longWayDown = new Book();
        longWayDown.setTitle("Long Way Down");
        longWayDown.setGenre("TRAVEL");
        longWayDown.setDescription("Boring story about something I cant remember.");

        // Save book data
        manager.saveEntity(harryPotter);
        manager.saveEntity(theGiver);
        manager.saveEntity(twilight);
        manager.saveEntity(longWayDown);
    }
}
