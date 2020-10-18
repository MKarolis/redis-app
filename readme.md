<h1>Simple Library book management system</h1>
<h6>
Created by Karolis Medekša for non-relational databases course<br/>
Vilnius University, Faculty of Mathematics and Informatics, 2020 Fall
</h6>

<h2>About</h2>
This is a very simple library book management system that uses `Redis` as a database.

JDK version 9 or higher is required to build and run the project

<h2>Setup</h2>
Application uses a redis database connection running on port `6379`.

To run the application with a new database setup is required, 
run the following commands in `redis-cli`:

```
hset user||1 name [Name for your admin user] password [SHA-256 hex of your admin user's password] authority ADMIN
set ticker||user 1
set ticker||book 0
set ticker||library 0
```

<h2>Usage</h2>
Build application `./gradlew build`

Run application `java -jar build/libs/RedisApp-[version].jar`

Commands:
- `REGISTER -u [userName] -pw [password]` Register a new user
- `LOGIN -u [userName] -pw [password]` Login
- `LOGOUT` Logout
- `BOOK_LIST` Get all books list
- `LIBRARY_LIST` Get all libraries list
- `AVAILABLE_BOOKS -lid [libraryId]` Get a list of all available books in a library
- `MY_BOOKS` List of all the books the current user has borrowed
- `ADD_LIBRARY -c [City]` Add a new library
- `INSERT_BOOK -t [title] -a [author]` Register a new book in the system
- `ADD_BOOK -lid [libraryId] -bid [bookId]` Add a new instance of a book to a library
- `TAKE_BOOK -lid [libraryId] -bid [bookId]` Take a book from a library
- `RETURN_BOOK -lid [libraryId] -bid [bookId]` Return a book to a library

<h2>Database structure</h2>
- Libraries, books and users are stored as hashes in form of `[entityType]||[entityId]`, 
for instance `book||1` or `user||2`
- Special key `lookup||user||[userName]` is used to lookup user id by his userName
- Keys `bookcount||[libraryId]` are used to store hashes of book counts in a library. 
Each field of the hash is book's id, value - book count.
- Keys `bookstaken||[userId]` are used to store hashes of books that a user has taken.
 Each field of the hash is book's id, value - book count.
- Keys `ticker||[entityType]` are used to store how many entities of each type are present.
