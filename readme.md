Library book management system

library: {
    id
    city
}

book : {
    id
    author
    name
    totalInventory
    available inventory
}

user : {
    id
    name
    password
    booksTaken
}

ticker:[user/book/library]

bookcount:libraryId {
    Map <bookId, bookCount>
}

booksTaken:libraryId {
    Map <bookId, bookCount>
}

lookup:user:name -> userId

two users cant take the same book
you cant take book while it's being transported