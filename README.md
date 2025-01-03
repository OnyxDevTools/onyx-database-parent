# Onyx Database

Onyx Database is a powerful, open-source, object-oriented database management system designed for flexibility and performance. It offers a robust API for managing persistent data, supporting embedded, in-memory, and remote database configurations.

## License

This project is licensed under the [GNU License](LICENSE).

## Authors

*   Tim Osborn ([tosborn@onyx.dev](https://github.com/timbob2000))
*   Chris Osborn ([cosborn@onyx.dev](https://github.com/cosbor11))

## Copyright

Copyright © 2025 Onyx Cloud Services, LLC. All rights reserved.

# Importing Onyx Database Gradle Dependencies

This guide explains how to import the necessary Gradle dependencies for using Onyx Database in your Kotlin project.

## Prerequisites

*   **GitHub Account:** You need a GitHub account with access to the [OnyxDevTools/onyx-database-parent](https://github.com/OnyxDevTools/onyx-database-parent) repository.
*   **GitHub Personal Access Token (PAT):**  You'll need a PAT with the `read:packages` scope to authenticate with GitHub Packages.
    1.  Go to your GitHub **Settings** -> **Developer settings** -> **Personal access tokens** -> **Tokens (classic)**.
    2.  Click **"Generate new token"** -> **"Generate new token (classic)"**
    3.  Give your token a descriptive name (e.g., "Onyx Database Access").
    4.  Select the `read:packages` scope.
    5.  Click **"Generate token"**.
    6.  **Important:** Copy your newly generated token immediately. You won't be able to see it again.

## Adding the GitHub Packages Repository

You need to configure Gradle to use the GitHub Packages repository where the Onyx Database artifacts are hosted.  Add the following to your `build.gradle.kts` (or `build.gradle` if you're using Groovy) file within the `repositories` block:

```kotlin
repositories {
    mavenCentral() // Or any other repositories you need
    maven {
        url = uri("[https://maven.pkg.github.com/OnyxDevTools/onyx-database-parent](https://maven.pkg.github.com/OnyxDevTools/onyx-database-parent)")
        credentials {
            username = "YOUR_GITHUB_USERNAME" // Replace with your GitHub username
            password = "YOUR_GITHUB_PERSONAL_ACCESS_TOKEN" // Replace with your PAT
        }
    }
}
```

# Table of Contents

1.  [Key Features](#key-features)
2.  [Core Components](#core-components)
    *   [PersistenceManager](#persistencemanager)
    *   [PersistenceManagerFactory](#persistencemanagerfactory)
    *   [SchemaContext](#schemacontext)
3.  [Getting Started](#getting-started)
    *   [1. Choose a PersistenceManagerFactory](#1-choose-a-persistencemanagerfactory)
        *   [Embedded Database Example (Kotlin)](#embedded-database-example-kotlin)
        *   [Remote Database Example (Kotlin)](#remote-database-example-kotlin)
    *   [2. Define Your Data Model](#2-define-your-data-model)
    *   [3. Use the PersistenceManager](#3-use-the-persistencemanager)
        *   [Saving an Entity (Kotlin)](#saving-an-entity-kotlin)
        *   [Querying for Entities (Kotlin)](#querying-for-entities-kotlin)
        *   [Deleting an Entity (Kotlin)](#deleting-an-entity-kotlin)
        *   [Updating an Entity (Kotlin)](#updating-an-entity-kotlin)
        *   [Batch Save Entities (Kotlin)](#batch-save-entities-kotlin)
        *   [Batch Delete Entities (Kotlin)](#batch-delete-entities-kotlin)
        *   [Executing an Update Query (Kotlin)](#executing-an-update-query-kotlin)
        *   [Executing a Delete Query (Kotlin)](#executing-a-delete-query-kotlin)
        *   [Streaming Data (Kotlin)](#streaming-data-kotlin)
        *   [Observing Data Changes (Kotlin)](#observing-data-changes-kotlin)
        *   [Count Entities (Kotlin)](#count-entities-kotlin)
        *   [List Entities (Kotlin)](#list-entities-kotlin)
        *   [Find Entity by ID (Kotlin)](#find-entity-by-id-kotlin)
        *   [Find Entity by ID in Partition (Kotlin)](#find-entity-by-id-in-partition-kotlin)
        *   [Check if Entity Exists (Kotlin)](#check-if-entity-exists-kotlin)
4.  [API Reference](#api-reference)
    *   [`PersistenceManager`](#persistencemanager-1)
    *   [`PersistenceManagerFactory`](#persistencemanagerfactory-1)
    *   [`EmbeddedPersistenceManagerFactory`](#embeddedpersistencemanagerfactory)
    *   [`RemotePersistenceManagerFactory`](#remotepersistencemanagerfactory)
5.  [Onyx Database Annotations](#onyx-database-annotations)
    *   [`@Attribute`](#attribute)
    *   [`@Identifier`](#identifier)
    *   [`@Partition`](#partition)
    *   [`@Relationship`](#relationship)
    *   [`@Entity`](#entity)
    *   [`@Index`](#index)
    *   [Lifecycle Annotations](#lifecycle-annotations)
    * 
## Key Features

*   **Object-Oriented:** Work with data naturally using your programming language's objects.
*   **Embedded, In-Memory, and Remote Modes:** Choose the deployment style that fits your needs.
*   **Database Server:** Create a Database Server
*   **Flexible Schema:** Define your data model with ease and adapt it as your application evolves.
*   **Powerful Querying:** Retrieve your data efficiently with a fluent and intuitive query language.
*   **Transactions:** Ensure data consistency with transactional operations.
*   **Indexing:** Optimize performance with custom indexing.
*   **Relationships:** Model complex data structures using relationships between entities.
*   **Lazy Loading:** Improve performance by loading related data only when needed.
*   **Caching:** Enhance query speed with built-in caching mechanisms.
*   **Security:** Protect your data with user authentication and encryption.
*   **Streaming:** Efficiently process large datasets with streaming support.
*   **Observability:** Monitor data changes with query listeners.
*   **Concurrency** Leverages optimized data structures to ensure data is safe while being used concurrently.
*   **Journaling** Record transactions to ensure safe recovery in the event of a failure.

## Core Components

### PersistenceManager

The `PersistenceManager` interface is the primary entry point for interacting with Onyx Database. It provides methods for:

*   **Saving Entities:** Persisting new or updated objects.
*   **Deleting Entities:** Removing objects from the database.
*   **Executing Queries:** Retrieving data based on specific criteria.
*   **Finding Entities:** Locating objects by ID or within a specific partition.
*   **Managing Relationships:** Handling associations between entities.
*   **Streaming Data:** Processing large datasets efficiently.
*   **Observing Changes:** Registering listeners to react to data modifications.

### PersistenceManagerFactory

The `PersistenceManagerFactory` interface is responsible for creating and configuring `PersistenceManager` instances. Onyx Database provides several implementations:

*   **`EmbeddedPersistenceManagerFactory`:** For creating embedded, file-based databases.
*   **`CachePersistenceManagerFactory`:** For creating in-memory databases. (Note: Not shown in the provided code but implied in the documentation.)
*   **`RemotePersistenceManagerFactory`:** For connecting to a remote Onyx Database server.


### SchemaContext

The `SchemaContext` holds metadata about your data model, including entity descriptors and indexing information. It plays a crucial role in how Onyx Database stores and retrieves data.

## Getting Started

### 1. Choose a PersistenceManagerFactory

Select the factory implementation that matches your desired database configuration (embedded, in-memory, or remote).

#### Embedded Database Example (Kotlin)

```kotlin
val factory = EmbeddedPersistenceManagerFactory("/path/to/your/database")
factory.setCredentials("username", "password")
factory.initialize()

val manager = factory.persistenceManager

// ... use the persistence manager ...

factory.close()
```

#### Remote Database Example (Kotlin)

```kotlin
val factory = RemotePersistenceManagerFactory("onx://your_database_host:port")
factory.setCredentials("username", "password")
factory.initialize()

val manager = factory.persistenceManager

// ... use the persistence manager ...

factory.close()
```


#### Remote Database Server Example (Kotlin)

```kotlin
val server = DatabaseServer("/local/path/to/data/files")
server.setCredentials("username", "password")
server.port = 8080
server.initialize()

server.start()
server.join()
```

### 2. Define Your Data Model

Create classes that implement the `IManagedEntity` interface to represent your data entities. Annotate fields to define relationships, indexes, and other properties. (Note: Annotation details are not provided in the given code but are implied.)

```kotlin
// Example Entity
@Entity
class User : ManagedEntity() {
   @Identifier
   var primaryKey: Any = 0
   @Attribute
   var name: String? = null
   @Index
   var email: String? = null

   // ... other fields, relationships, etc. ...
}
```

### 3. Use the PersistenceManager

Interact with your database through the `PersistenceManager` instance.

#### Saving an Entity (Kotlin)

```kotlin
val user = User()
user.name = "John Doe"
user.email = "[email address removed]"

val savedUser = manager.saveEntity(user)
```

#### Finding an Entity (Kotlin)

```kotlin
val userToFind = manager.findById<User>(1)
```

#### Finding an Entity Within Partition (Kotlin)

```kotlin
val userToFind = manager.findByIdInPartition<User>(1, "partitionId")
```

#### Identify if an entity exists

```kotlin
val user = User()
user.id = "UserId"

val exists = manager.exists(user)
```

#### Deleting an Entity (Kotlin)

```kotlin
val userToDelete = manager.findById<User>(1)
if (userToDelete != null) {
    manager.deleteEntity(userToDelete)
}
```

#### Querying for Entities (Kotlin)

```kotlin
val users = manager.executeQuery<User>(
    Query(User::class.java)
        .where("name" eq "John Doe")
)
```

#### Updating an Entity (Kotlin)

```kotlin
//Get the user with id 5
val user = manager.findById<User>(5) ?: return

//Update the user's email address
user.email = "[email address removed]"

//Save the changes to the user
manager.save(user)
```

#### Batch Save Entities (Kotlin)

```kotlin
val user1 = User()
user1.firstName = "John"
user1.lastName = "Doe"

val user2 = User()
user2.firstName = "Jane"
user2.lastName = "Doe"

val user3 = User()
user3.firstName = "Jack"
user3.lastName = "Doe"

//Save all of the entities
manager.save(listOf(user1, user2, user3))
```

#### Batch Delete Entities (Kotlin)

```kotlin
//Get the first 3 users
val users = manager.from<User>()
    .limit(3)
    .list<User>()

//Delete the users
manager.delete(users)
```

#### Executing an Update Query (Kotlin)

```kotlin
//Update all of the users whose last name is Doe to have a first name of Jacob
val updatedUsers = manager.from<User>()
    .where("lastName" eq "Doe")
    .update("firstName" setTo "Jacob")
    .executeUpdate()
```

#### Executing a Delete Query (Kotlin)

```kotlin
//Delete all of the users whose last name is Doe
val deletedUsers = manager.from<User>()
    .where("lastName" eq "Doe")
    .executeDelete()
```

#### Streaming Data (Kotlin)

```kotlin
manager.stream<User> { user ->
    // Process each user entity
    println("Processing user: ${user.name}")
    true // Continue streaming
}
```

#### Observing Data Changes (Kotlin)

```kotlin
val query = Query(User::class.java).where("name" eq "John Doe")
query.changeListener = object : QueryListener<User> {
    override fun onChange(event: QueryListenerEvent, results: List<User>) {
        when (event) {
            QueryListenerEvent.INSERT -> println("New user added: ${results.firstOrNull()?.name}")
            QueryListenerEvent.UPDATE -> println("User updated: ${results.firstOrNull()?.name}")
            QueryListenerEvent.DELETE -> println("User deleted")
        }
    }
}
manager.listen(query)
```

## API Reference

### `PersistenceManager`


| Method                                                                                                                                | Description                                                                                                                                     |
|:--------------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------|
| `context`                                                                                                                             | Gets or sets the `SchemaContext` for the database.                                                                                              |
| `saveEntity(entity: E): E`                                                                                                            | Saves a single entity (insert or update).                                                                                                       |
| `save(entity: E): E`                                                                                                                  | Alias for `saveEntity`.                                                                                                                         |
| `saveEntities(entities: List<IManagedEntity>)`                                                                                        | Saves a list of entities in a batch.                                                                                                            |
| `save(entities: List<IManagedEntity>)`                                                                                                | Alias for `saveEntities`.                                                                                                                       |
| `deleteEntity(entity: IManagedEntity): Boolean`                                                                                       | Deletes a single entity.                                                                                                                        |
| `delete(entity: IManagedEntity): Boolean`                                                                                             | Alias for `deleteEntity`.                                                                                                                       |
| `deleteEntities(entities: List<IManagedEntity>)`                                                                                      | Deletes a list of entities.                                                                                                                     |
| `delete(entities: List<IManagedEntity>)`                                                                                              | Alias for `deleteEntities`.                                                                                                                     |
| `executeDelete(query: Query): Int`                                                                                                    | Executes a delete query and returns the number of deleted entities.                                                                             |
| `executeDeleteForResult(query: Query): QueryResult`                                                                                   | Executes a delete query and returns a `QueryResult` object.                                                                                     |
| `executeUpdate(query: Query): Int`                                                                                                    | Executes an update query and returns the number of updated entities.                                                                            |
| `executeUpdateForResult(query: Query): QueryResult`                                                                                   | Executes an update query and returns a `QueryResult` object.                                                                                    |
| `executeQuery(query: Query): List<E>`                                                                                                 | Executes a query and returns a list of results.                                                                                                 |
| `executeQueryForResult(query: Query): QueryResult`                                                                                    | Executes a query and returns a `QueryResult` object.                                                                                            |
| `executeLazyQuery(query: Query): List<E>`                                                                                             | Executes a query and returns a `LazyQueryCollection` for lazy loading of results.                                                               |
| `executeLazyQueryForResult(query: Query): QueryResult`                                                                                | Executes a lazy query and returns a `QueryResult` object.                                                                                       |
| `find(entity: IManagedEntity): E`                                                                                                     | Hydrates an entity based on its primary key and partition key (if applicable).                                                                  |
| `findById(clazz: Class<*>, id: Any): E?`                                                                                              | Finds an entity by its primary key.                                                                                                             |
| `findByIdInPartition(clazz: Class<*>, id: Any, partitionId: Any): E?`                                                                 | Finds an entity by its primary key within a specific partition.                                                                                 |
| `exists(entity: IManagedEntity): Boolean`                                                                                             | Checks if an entity exists based on its primary key and partition key (if applicable).                                                          |
| `exists(entity: IManagedEntity, partitionId: Any): Boolean`                                                                           | Checks if an entity exists based on its primary key within a specific partition.                                                                |
| `initialize(entity: IManagedEntity, attribute: String)`                                                                               | Force-hydrates a relationship for an entity.                                                                                                    |
| `getRelationship<T : Any?>(entity: IManagedEntity, attribute: String): T`                                                             | Gets the value of a relationship for an entity.                                                                                                 |
| `list(clazz: Class<*>): List<E>`                                                                                                      | Returns a list of all entities of a given type.                                                                                                 |
| `list(clazz: Class<*>, criteria: QueryCriteria): List<E>`                                                                             | Returns a list of entities matching the given criteria.                                                                                         |
| `list(clazz: Class<*>, criteria: QueryCriteria, orderBy: Array<QueryOrder>): List<E>`                                                 | Returns a list of entities matching the given criteria, sorted by the specified order.                                                          |
| `list(clazz: Class<*>, criteria: QueryCriteria, orderBy: QueryOrder): List<E>`                                                        | Returns a list of entities matching the given criteria, sorted by the specified order.                                                          |
| `list(clazz: Class<*>, criteria: QueryCriteria, partitionId: Any): List<E>`                                                           | Returns a list of entities matching the given criteria within a specific partition.                                                             |
| `list(clazz: Class<*>, criteria: QueryCriteria, orderBy: Array<QueryOrder>, partitionId: Any): List<E>`                               | Returns a list of entities matching the given criteria within a specific partition, sorted by the specified order.                              |
| `list(clazz: Class<*>, criteria: QueryCriteria, orderBy: QueryOrder, partitionId: Any): List<E>`                                      | Returns a list of entities matching the given criteria within a specific partition, sorted by the specified order.                              |
| `list(clazz: Class<*>, criteria: QueryCriteria, start: Int, maxResults: Int, orderBy: Array<QueryOrder>?): List<E>`                   | Returns a list of entities matching the given criteria, within a specified range and sorted by the specified order.                             |
| `list(clazz: Class<*>, criteria: QueryCriteria, start: Int, maxResults: Int, orderBy: Array<QueryOrder>?, partitionId: Any): List<E>` | Returns a list of entities matching the given criteria within a specific partition, within a specified range and sorted by the specified order. |
| `saveRelationshipsForEntity(entity: IManagedEntity, relationship: String, relationshipIdentifiers: Set<Any>)`                         | Batch-saves relationships for an entity.                                                                                                        |
| `getWithReference(entityType: Class<*>, reference: Reference): E?`                                                                    | Gets an entity by its partition reference.                                                                                                      |
| `findByIdWithPartitionId(clazz: Class<*>, id: Any, partitionId: Long): E`                                                             | Retrieves an entity using the primaryKey and partition                                                                                          |
| `stream(query: Query, streamer: QueryStream<T>)`                                                                                      | Streams data entities based on a query.                                                                                                         |
| `stream(query: Query, action: (T) -> Boolean)`                                                                                        | Streams data entities based on a query using a lambda                                                                                           |
| `stream(query: Query, queryStreamClass: Class<*>)`                                                                                    | Streams data entities based on a query using a class instance.                                                                                  |
| `getMapWithReferenceId(entityType: Class<*>, reference: Reference): Map<String, *>?`                                                  | Gets a map representation of an entity with a reference ID.                                                                                     |
| `countForQuery(query: Query): Long`                                                                                                   | Returns the number of entities that match the query criteria.                                                                                   |
| `removeChangeListener(query: Query): Boolean`                                                                                         | Unregisters a query listener.                                                                                                                   |
| `listen(query: Query)`                                                                                                                | Registers a query listener.                                                                                                                     |
| `listen(query: Query, queryListener: QueryListener<*>)`                                                                               | Registers a query listener with a specific `QueryListener` instance.                                                                            |
| `executeLazyQueryForResults(query: Query): QueryResult`                                                                               | Executes a lazy query and returns a `QueryResult` object.                                                                                       |
| `findRelationship(entity: IManagedEntity, attribute: String): Any?`                                                                   | Hydrate a relationship and return the key                                                                                                       |

### `PersistenceManagerFactory`

| Method                                           | Description                                                               |
|:-------------------------------------------------|:--------------------------------------------------------------------------|
| `credentials`                                    | Gets the formatted credentials for authentication.                        |
| `maxCardinality`                                 | Gets or sets the maximum number of records that can be scanned per query. |
| `persistenceManager`                             | Gets the `PersistenceManager` instance.                                   |
| `databaseLocation`                               | Gets the location of the database (file path or remote endpoint).         |
| `schemaContext`                                  | Gets or sets the `SchemaContext` for the database.                        |
| `encryption`                                     | Gets or sets the `EncryptionInteractor` for encrypting data.              |
| `encryptDatabase`                                | Gets or sets whether the database should be encrypted.                    |
| `storeType`                                      | Gets or sets the storage type (e.g., memory-mapped file or NIO file).     |
| `initialize()`                                   | Initializes the database connection and storage mechanisms.               |
| `close()`                                        | Safely shuts down the database.                                           |
| `setCredentials(user: String, password: String)` | Sets the username and password for authentication.                        |

### `EmbeddedPersistenceManagerFactory`

| Method                                           | Description                                                               |
|:-------------------------------------------------|:--------------------------------------------------------------------------|
| `databaseLocation`                               | Gets the location of the database (file path or remote endpoint).         |
| `instance`                                       | Gets the instance name of the database.                                   |
| `schemaContext`                                  | Gets or sets the `SchemaContext` for the database.                        |
| `addShutdownHook`                                | Gets or sets whether a shutdown hook should be added to the runtime.      |
| `encryption`                                     | Gets or sets the `EncryptionInteractor` for encrypting data.              |
| `storeType`                                      | Gets or sets the storage type (e.g., memory-mapped file or NIO file).     |
| `encryptDatabase`                                | Gets or sets whether the database should be encrypted.                    |
| `credentials`                                    | Gets the formatted credentials for authentication.                        |
| `maxCardinality`                                 | Gets or sets the maximum number of records that can be scanned per query. |
| `persistenceManager`                             | Gets the `PersistenceManager` instance.                                   |
| `isEnableJournaling`                             | Gets or sets whether journaling is enabled.                               |
| `setCredentials(user: String, password: String)` | Sets the username and password for authentication.                        |
| `initialize()`                                   | Initializes the databasØe connection and storage mechanisms.              |
| `close()`                                        | Safely shuts down the database.                                           |

### `RemotePersistenceManagerFactory`

| Method                                           | Description                                                               |
|:-------------------------------------------------|:--------------------------------------------------------------------------|
| `storeType`                                      | Gets or sets the storage type (e.g., memory-mapped file or NIO file).     |
| `keepAlive`                                      | Gets or sets whether the connection should be kept alive.                 |
| `databaseLocation`                               | Gets the location of the database (file path or remote endpoint).         |
| `credentials`                                    | Gets the formatted credentials for authentication.                        |
| `maxCardinality`                                 | Gets or sets the maximum number of records that can be scanned per query. |
| `persistenceManager`                             | Gets the `PersistenceManager` instance.                                   |
| `schemaContext`                                  | Gets or sets the `SchemaContext` for the database.                        |
| `encryption`                                     | Gets or sets the `EncryptionInteractor` for encrypting data.              |
| `encryptDatabase`                                | Gets or sets whether the database should be encrypted.                    |
| `setCredentials(user: String, password: String)` | Sets the username and password for authentication.                        |
| `initialize()`                                   | Initializes the database connection and storage mechanisms.               |
| `close()`                                        | Safely shuts down the database.                                           |
| `service<T>(name: String, type: Class<*>): T`    | Gets a remote service.                                                    |

## Onyx Database Annotations

Onyx Database utilizes annotations to define the structure and behavior of your data model. These annotations provide a declarative way to map your classes and fields to the database schema.

### `@Attribute`

This annotation marks a field within an `IManagedEntity` as a persistent attribute.

**Supported Attribute Types:**

*   `Long`
*   `long`
*   `Integer`
*   `int`
*   `Double`
*   `double`
*   `Float`
*   `float`
*   `String`
*   `Boolean`
*   `boolean`
*   `kotlin.Array`
*   `java.util.ArrayList`
*   `java.util.Vector`

**Parameters:**

*   `nullable: Boolean` (optional, defaults to `true`): Indicates whether the attribute can hold a `null` value.
*   `size: Int` (optional, defaults to `-1`): Specifies the maximum size of the attribute. This is primarily applicable to `String` attributes.

**Example:**

```kotlin
@Entity
class Person : IManagedEntity {
    @Attribute(nullable = false, size = 200)
    var firstName: String? = null

    // ... other fields
}
```

### `@Identifier`

This annotation designates a field as the primary key of an `IManagedEntity`.

**Important:** The `@Attribute` annotation is still required along with `@Identifier`.

**Parameters:**

*   `generator: IdentifierGenerator` (optional, defaults to `IdentifierGenerator.NONE`): Specifies the strategy for generating primary key values.
    *   `IdentifierGenerator.NONE`: No automatic generation (you must provide the primary key value).
    *   `IdentifierGenerator.SEQUENCE`: Auto-generates a numeric sequence. (Requires the field to be a numeric type).

**Example:**

```kotlin
@Entity
class Person : IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute(nullable = false)
    override var primaryKey: Long = 0

    // ... other fields
}
```

### `@Partition`

This annotation indicates that an `IManagedEntity` should be partitioned based on the value of the annotated field. Partitioning can improve performance for large datasets by dividing them into smaller, more manageable segments.

**Important:** The `@Attribute` annotation is still required along with `@Partition`.

**Example:**

```kotlin
@Entity
class Product : IManagedEntity {
    @Partition
    @Attribute(nullable = false)
    var categoryId: Long = 0

    // ... other fields
}
```

### `@Relationship`

This annotation defines a relationship between two `IManagedEntity` classes.

**Parameters:**

*   `type: RelationshipType`: Specifies the type of relationship (e.g., `ONE_TO_ONE`, `MANY_TO_MANY`, etc.).
*   `inverseClass: KClass<*>`: The class of the related entity on the other side of the relationship.
*   `inverse: String` (optional): The name of the field in the related entity that represents the inverse side of the relationship.
*   `fetchPolicy: FetchPolicy` (optional, defaults to `FetchPolicy.LAZY`): Determines how related entities are loaded:
    *   `FetchPolicy.LAZY`: Related entities are loaded only when accessed.
    *   `FetchPolicy.EAGER`: Related entities are loaded immediately along with the primary entity.
    *   `FetchPolicy.NONE`: Related entities are not loaded automatically.
*   `cascadePolicy: CascadePolicy` (optional, defaults to `CascadePolicy.NONE`): Specifies how operations on the primary entity should cascade to related entities:
    *   `CascadePolicy.NONE`: No cascading.
    *   `CascadePolicy.SAVE`: Related entities are saved when the primary entity is saved.
    *   `CascadePolicy.DELETE`: Related entities are deleted when the primary entity is deleted.
    *   `CascadePolicy.ALL`: Both `SAVE` and `DELETE` are applied.
    *   `CascadePolicy.DEFER_SAVE`: Saves to related entities are deferred (useful for batch operations).

**Example:**

```kotlin
@Entity
class Order : IManagedEntity {
    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = OrderItem::class, inverse = "order", fetchPolicy = FetchPolicy.LAZY, cascadePolicy = CascadePolicy.ALL)
    var items: List<OrderItem>? = null

    // ... other fields
}

@Entity
class OrderItem : IManagedEntity {
    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = Order::class, inverse = "items")
    var order: Order? = null

    // ... other fields
}
```

### `@Entity`

This annotation marks a class as a managed entity, making it eligible for persistence within the Onyx Database.

**Important:** Classes annotated with `@Entity` must implement the `IManagedEntity` interface.

**Parameters**
* `fileName: String` (optional, defaults to `"")`: Specifies the name of the file the entity will be stored in.  This is used to control how data is stored on disk
* `archiveDirectories: Array<String>` (optional, defaults to empty array):  Specifies the directories where archive files for this entity will be stored.  This is useful for managing long term storage.

**Example:**

```kotlin
@Entity
class User : IManagedEntity {
    // ... fields and methods
}
```

### `@Index`

This annotation marks a field as indexed, which can significantly improve the performance of queries that filter or sort based on that field.

**Important:** The `@Attribute` annotation is still required along with `@Index`.

**Example:**

```kotlin
@Entity
class Product : IManagedEntity {
    @Index
    @Attribute(nullable = false, size = 100)
    var name: String? = null

    // ... other fields
}
```

### Lifecycle Annotations

Onyx Database provides a set of annotations to define methods that should be invoked at specific points in an entity's lifecycle:

*   `@PrePersist`: Executed before an entity is saved (either inserted or updated).
*   `@PostPersist`: Executed after an entity is saved (either inserted or updated).
*   `@PreInsert`: Executed before an entity is inserted.
*   `@PostInsert`: Executed after an entity is inserted.
*   `@PreUpdate`: Executed before an entity is updated.
*   `@PostUpdate`: Executed after an entity is updated.
*   `@PreRemove`: Executed before an entity is deleted.
*   `@PostRemove`: Executed after an entity is deleted.

**Example:**

```kotlin
@Entity
class LogEntry : IManagedEntity {
    @Attribute
    var timestamp: Long = 0

    @PrePersist
    fun beforePersist() {
        timestamp = System.currentTimeMillis()
    }
}
```