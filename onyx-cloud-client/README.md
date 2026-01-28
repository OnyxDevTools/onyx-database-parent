# onyx-cloud-client (Kotlin/JVM)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](../LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/dev.onyx/onyx-cloud-client.svg)](https://central.sonatype.com/artifact/dev.onyx/onyx-cloud-client)

Kotlin/JVM client SDK for **Onyx Cloud Database** — a typed, builder-pattern API for querying and persisting data in Onyx. Includes:

- A fluent query builder with type-safe condition helpers
- Credential resolution (explicit config → env → config file chain → home profile)
- Pagination and streaming support for real-time data
- Cascade operations for managing relationships
- AI/Chat API (ChatGPT-compatible)
- Schema API for managing database schemas
- Secrets API for secure credential storage
- Document storage for binary assets
- Full-text (Lucene) search capabilities

- **Website:** https://onyx.dev/
- **Cloud Console:** https://cloud.onyx.dev
- **Docs hub:** https://onyx.dev/documentation/
- **Cloud API docs:** https://onyx.dev/documentation/api-documentation/

---

## Getting started (Cloud ➜ keys ➜ connect)

1. **Sign up & create resources** at **https://cloud.onyx.dev**  
   Create an **Organization**, then a **Database**, define your **Schema** (e.g., `User`, `Role`, `Permission`), and create **API Keys**.

2. **Note your connection parameters**:
   You will need to setup an apiKey to connect to your database in the onyx console at <https://cloud.onyx.dev>. After creating the apiKey, you can download the `onyx-database.json`. Save it to the `config` folder.
   
   The SDK uses a config resolution chain. A recommended layout is:
   ```
   your-project/
   ├── config/
   │   └── onyx-database.json
   └── src/
   ```

3. **Add the SDK** to your project (see [Install](#install)).

4. **Initialize the client** (see [Initialize the client](#initialize-the-client)).

> Supports **Java 17+** and **Kotlin 1.9+**.

---

## Install

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("dev.onyx:onyx-cloud-client:3.8.6")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'dev.onyx:onyx-cloud-client:3.8.6'
}
```

### Maven

```xml
<dependency>
    <groupId>dev.onyx</groupId>
    <artifactId>onyx-cloud-client</artifactId>
    <version>3.8.6</version>
</dependency>
```

---

## Initialize the client

This SDK resolves credentials automatically using the chain:

**explicit config → environment variables → `ONYX_CONFIG_PATH` file → project config file → home profile**

Call `onyx.init()` to use the automatic resolution, or pass credentials directly.

### Option A) Config files (recommended)

Create a project config file:

- `./config/onyx-database.json` (recommended layout), or
- `./onyx-database.json` (repo root)

Example: `config/onyx-database.json`

```json
{
  "databaseId": "YOUR_DATABASE_ID",
  "baseUrl": "https://api.onyx.dev",
  "aiBaseUrl": "https://ai.onyx.dev",
  "defaultModel": "onyx",
  "apiKey": "YOUR_API_KEY",
  "apiSecret": "YOUR_API_SECRET"
}
```

Then initialize:

```kotlin
import com.onyx.cloud.api.onyx

val db = onyx.init<Any>()  // resolves config via the standard chain
```

### Option B) Environment variables

Set the following environment variables:

- `ONYX_DATABASE_ID`
- `ONYX_DATABASE_BASE_URL` (defaults to `https://api.onyx.dev`)
- `ONYX_DATABASE_API_KEY`
- `ONYX_DATABASE_API_SECRET`
- `ONYX_AI_BASE_URL` (defaults to `https://ai.onyx.dev`)
- `ONYX_DEFAULT_MODEL` (defaults to `onyx`)
- `ONYX_PARTITION` (optional default partition)
- `ONYX_DEBUG` (set to `true` for request/response logging)

```kotlin
import com.onyx.cloud.api.onyx

val db = onyx.init<Any>()
```

### Option C) Explicit config (direct)

```kotlin
import com.onyx.cloud.api.onyx
import com.onyx.cloud.api.OnyxConfig

val db = onyx.init<Any>(OnyxConfig(
    baseUrl = "https://api.onyx.dev",
    databaseId = "YOUR_DATABASE_ID",
    apiKey = "YOUR_API_KEY",
    apiSecret = "YOUR_API_SECRET",
    partition = "tenantA",                // optional default partition
    requestLoggingEnabled = true,         // optional request logging
    responseLoggingEnabled = true,        // optional response logging
    aiBaseUrl = "https://ai.onyx.dev",    // optional AI endpoint
    defaultModel = "onyx"                 // optional default model
))
```

### Option D) Simple direct initialization

```kotlin
import com.onyx.cloud.api.onyx

val db = onyx.init(
    baseUrl = "https://api.onyx.dev",
    databaseId = "YOUR_DATABASE_ID",
    apiKey = "YOUR_API_KEY",
    apiSecret = "YOUR_API_SECRET"
)
```

#### Configuration options

| Parameter | Description |
|-----------|-------------|
| `baseUrl` | Onyx API endpoint (defaults to `https://api.onyx.dev`) |
| `databaseId` | Your database identifier |
| `apiKey` | API key for authentication |
| `apiSecret` | API secret for authentication |
| `partition` | Default partition for queries |
| `requestLoggingEnabled` | Log HTTP requests (sensitive headers redacted) |
| `responseLoggingEnabled` | Log HTTP responses |
| `requestTimeoutMs` | Non-streaming read timeout (default: 120,000ms) |
| `connectTimeoutMs` | Socket connect timeout (default: 30,000ms) |
| `aiBaseUrl` | AI API endpoint (defaults to `https://ai.onyx.dev`) |
| `defaultModel` | Default AI model (defaults to `onyx`) |

#### Debug mode

Setting `ONYX_DEBUG=true` enables both request/response logging and also logs which credential source was used.

---

## Use Onyx AI (ChatGPT-compatible)

Onyx AI shares the same key/secret as the database client. Use `db.ai` for chat, models, and script approvals; `db.chat("...")` provides a shorthand for quick completions.

```kotlin
import com.onyx.cloud.api.onyx
import com.onyx.cloud.impl.ChatRequest
import com.onyx.cloud.impl.ChatMessage

val db = onyx.init<Any>()

// Shorthand chat completion (returns first message content)
val quick = db.chat("Summarize last week's signups.")
println(quick)

// Full request via db.ai
val completion = db.ai.chat(ChatRequest(
    model = "onyx-chat",
    messages = listOf(ChatMessage("user", "Summarize last week's signups."))
))
println(completion.choices.first().message.content)

// Override shorthand defaults
val custom = db.chat(
    prompt = "List three colors.",
    model = "onyx-chat",
    role = "user",
    temperature = 0.2
)

// Models metadata
val models = db.ai.getModels()
println(models.data.map { it.id })
```

### Streaming chat

```kotlin
db.ai.chatStream(
    ChatRequest(
        model = "onyx-chat",
        messages = listOf(ChatMessage("user", "Draft an onboarding email."))
    )
) { chunk ->
    chunk.choices.firstOrNull()?.delta?.content?.let { 
        print(it)
    }
}
```

### Script mutation approvals

```kotlin
val approval = db.ai.requestScriptApproval("db.save({ 'table': 'User', 'id': '123' })")
if (approval.requiresApproval) {
    println("Approval needed: ${approval.findings}")
}
```

---

## Query helpers at a glance

Import condition and sort helpers:

```kotlin
import com.onyx.cloud.api.*
```

### Condition operators

| Helper | Description | Example |
|--------|-------------|---------|
| `eq` | Equal | `"status" eq "active"` |
| `neq` | Not equal | `"status" neq "deleted"` |
| `gt` | Greater than | `"age" gt 18` |
| `gte` | Greater than or equal | `"age" gte 21` |
| `lt` | Less than | `"price" lt 100` |
| `lte` | Less than or equal | `"score" lte 50` |
| `inOp` | In list | `"status" inOp listOf("active", "pending")` |
| `notIn` | Not in list | `"role" notIn listOf("guest")` |
| `between` | Range (inclusive) | `"age" between (18 to 65)` |
| `like` | Pattern match (SQL LIKE) | `"name" like "%john%"` |
| `notLike` | Pattern not match | `"email" notLike "%spam%"` |
| `contains` | Contains substring | `"email" contains "@example.com"` |
| `containsIgnoreCase` | Contains (case-insensitive) | `"name" containsIgnoreCase "john"` |
| `notContains` | Does not contain | `"tags" notContains "archived"` |
| `startsWith` | Starts with prefix | `"name" startsWith "Dr."` |
| `notStartsWith` | Does not start with | `"code" notStartsWith "TEST"` |
| `matches` | Regex match | `"phone" matches "\\d{3}-\\d{4}"` |
| `notMatches` | Regex not match | `"id" notMatches "temp_.*"` |
| `isNull()` | Is null | `"deletedAt".isNull()` |
| `notNull()` | Is not null | `"email".notNull()` |

### Sort helpers

```kotlin
asc("createdAt")   // Ascending order
desc("updatedAt")  // Descending order
```

### Aggregate functions (for select clauses)

```kotlin
import com.onyx.cloud.api.*

avg("price")           // Average
sum("quantity")        // Sum
count("id")            // Count
min("price")           // Minimum
max("price")           // Maximum
std("score")           // Standard deviation
variance("score")      // Variance
median("age")          // Median
percentile("score", 95) // Percentile
```

### String transform functions

```kotlin
upper("name")                          // Uppercase
lower("email")                         // Lowercase
substring("description", 0, 100)       // Substring
replace("text", "old", "new")          // Replace
```

---

## Usage examples

> The examples assume your schema has entities like `User`, `Product`, etc.

### 1) List (query & paging)

```kotlin
import com.onyx.cloud.api.*

// Fetch first 25 active users ordered by creation date
val page1 = db.from<User>()
    .where("status" eq "active")
    .and("email" contains "@example.com")
    .orderBy(asc("createdAt"))
    .pageSize(25)
    .list<User>()

// Process items on current page
page1.forEachOnPage { user -> println(user.name) }

// Fetch next page if available
page1.nextPage?.let { token ->
    val page2 = db.from<User>()
        .where("status" eq "active")
        .orderBy(asc("createdAt"))
        .pageSize(25)
        .nextPage(token)
        .list<User>()
}

// Or iterate through ALL pages automatically
page1.forEachAll { user ->
    println("Processing user: ${user.name}")
    true // return true to continue, false to stop
}
```

### 2) First or null

```kotlin
val maybeUser = db.from<User>()
    .where("email" eq "alice@example.com")
    .firstOrNull<User>()
```

### 3) Count

```kotlin
val activeCount = db.from<User>()
    .where("status" eq "active")
    .count()
```

### 4) Save (create/update)

```kotlin
// Save a single entity
val savedUser = db.save(User(
    id = "user_123",
    email = "alice@example.com",
    status = "active"
))

// Batch save multiple entities
db.batchSave(listOf(
    User(id = "user_124", email = "bob@example.com"),
    User(id = "user_125", email = "carol@example.com")
), batchSize = 500)
```

### 5) Find by ID

```kotlin
val user = db.findById<User>("user_123")

// With partition
val partitionedUser = db.findByIdInPartition<User>(
    primaryKey = "user_123",
    partition = "tenantA"
)
```

### 6) Delete (by primary key)

```kotlin
val success = db.delete<User>("user_123")

// With partition
db.deleteInPartition<User>("user_123", "tenantA")
```

### 7) Delete using query

```kotlin
val deletedCount = db.from<User>()
    .where("status" eq "inactive")
    .delete()
```

### 8) Update using query

```kotlin
val updatedCount = db.from<User>()
    .where("id" eq "user_123")
    .setUpdates("status" to "inactive", "updatedAt" to Date())
    .update()
```

### 9) Select specific fields

```kotlin
val userNamesAndEmails = db.select("name", "email")
    .from<User>()
    .where("status" eq "active")
    .list<HashMap<String, Any>>()
```

### 10) Aggregation with group by

```kotlin
val categoryStats = db.from<Product>()
    .select("category", avg("price"), count("id"))
    .groupBy("category")
    .list<HashMap<String, Any>>()
```

---

## Schema API

Manage database schemas programmatically.

```kotlin
// Get current schema
val schema = db.getSchema(tables = listOf("User", "Profile"))

// Get schema history
val history = db.getSchemaHistory()

// Validate a schema without publishing
val validation = db.validateSchema(mapOf(
    "revisionDescription" to "Add profile triggers",
    "entities" to listOf(
        mapOf(
            "name" to "Profile",
            "identifier" to mapOf("name" to "id", "generator" to "UUID"),
            "attributes" to listOf(
                mapOf("name" to "id", "type" to "String", "isNullable" to false),
                mapOf("name" to "userId", "type" to "String", "isNullable" to false)
            )
        )
    )
))

// Update and publish schema
val result = db.updateSchema(
    schema = mapOf(
        "revisionDescription" to "Publish profile changes",
        "entities" to listOf(/* ... */)
    ),
    publish = true
)

// Diff local schema vs API
val diff = db.diffSchema(localSchema)
println("Added: ${diff.addedTables}")
println("Removed: ${diff.removedTables}")
println("Changed: ${diff.changedTables}")
```

---

## Secrets API

Store and retrieve secrets securely.

```kotlin
import com.onyx.cloud.impl.SecretInput

// List all secrets
val secrets = db.listSecrets()

// Get a secret by name
val secret = db.getSecret("api-key")
println(secret?.value)

// Create or update a secret
db.putSecret("api-key", SecretInput(
    value = "super-secret-value",
    purpose = "Access to external API"
))

// Delete a secret
db.deleteSecret("api-key")
```

---

## Cascade operations

Cascade operations allow saving or deleting entities along with their related data.

### Cascade save

```kotlin
val userWithPosts = User(
    id = "user_123",
    name = "John Doe",
    posts = listOf(
        Post(title = "First Post"),
        Post(title = "Second Post")
    )
)

// Saves user AND all associated posts
db.cascade("posts").save(userWithPosts)
```

### Cascade delete

```kotlin
// Deletes user and cascades to specified relationships
db.cascade("orders", "profile").delete<User>("user_123")
```

---

## Full-text (Lucene) search

Use `.search(...)` for full-text queries across indexed fields.

### Table-specific search

```kotlin
val results = db.from<Article>()
    .search("storm warning", minScore = 0.5f)
    .list<Article>()
```

### Cross-table search

```kotlin
val allResults = db.search("product engineer", minScore = 0.42f)
    .list<FullTextSearchResult>()

allResults.forEach { result ->
    println("Found ${result.entityType}: ${result.entity}")
}
```

### Combine search with filters

```kotlin
val filteredSearch = db.from<User>()
    .where(search("engineer", 0.5f))
    .and("status" eq "active")
    .list<User>()
```

---

## Streaming (live changes)

Subscribe to real-time data changes using the streaming API.

```kotlin
val subscription = db.from<Message>()
    .where("chatRoomId" eq "room-42")
    .onItemAdded { message: Message -> 
        println("New message: ${message.text}") 
    }
    .onItemUpdated { message: Message -> 
        println("Edited: ${message.text}") 
    }
    .onItemDeleted { message: Message -> 
        println("Deleted: ${message.id}") 
    }
    .stream<Message>(includeQueryResults = true, keepAlive = true)

// Later, stop the stream
subscription.cancel()

// Or cancel and wait for cleanup
subscription.cancelAndJoin()

// Auto-close with use block
db.from<Event>().stream<Event>().use { stream ->
    // Stream is active inside this block
    Thread.sleep(10_000)
} // Automatically closed
```

---

## Documents API (binary assets)

Store and retrieve binary documents (images, files, etc.).

### Save a document

```kotlin
val doc = OnyxDocument(
    documentId = "logo.png",
    path = "/brand/logo.png",
    mimeType = "image/png",
    content = "iVBORw0KGgoAAA..."  // Base64 encoded
)
val savedDoc = db.saveDocument(doc)
```

### Retrieve a document

```kotlin
// Get with optional resizing for images
val image = db.getDocument("logo.png", DocumentOptions(width = 128, height = 128))
```

### Delete a document

```kotlin
db.deleteDocument("logo.png")
```

---

## Error handling

```kotlin
import com.onyx.cloud.exceptions.NotFoundException

try {
    val user = db.findById<User>("nonexistent")
} catch (e: NotFoundException) {
    println("User not found: ${e.message}")
} catch (e: IllegalStateException) {
    println("Config error: ${e.message}")
} catch (e: RuntimeException) {
    println("API error: ${e.message}")
}
```

---

## Connection handling

Calling `onyx.init()` returns a lightweight client. Configuration is resolved once and cached for a short TTL (configurable) to avoid repeated credential lookups. Use `onyx.clearCacheConfig()` to force re-resolution of credentials.

```kotlin
// Clear cached config (useful when credentials rotate)
onyx.clearCacheConfig()
```

---

## Cleanup

Close the client to cancel all active streams:

```kotlin
db.close()
```

---

## Related links

- Onyx website: https://onyx.dev/
- Cloud console: https://cloud.onyx.dev
- Docs hub: https://onyx.dev/documentation/
- Cloud API docs: https://onyx.dev/documentation/api-documentation/

---

## License

MIT © Onyx Dev Tools. See [LICENSE](../LICENSE).

---

> **Keywords:** Onyx Database Kotlin SDK, Onyx Cloud Database, Onyx NoSQL Graph Database client, Kotlin query builder, JVM database client, streaming, real-time data, cascade operations, AI chat, schema management
