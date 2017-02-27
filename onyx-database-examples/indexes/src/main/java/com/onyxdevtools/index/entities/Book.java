package com.onyxdevtools.index.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

@SuppressWarnings("unused")
@Entity
public class Book extends ManagedEntity implements IManagedEntity
{

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    private long bookId;

    @Attribute
    private String title;

    @Index
    @Attribute
    private String genre;

    @Attribute
    private String description;

    public long getBookId() {
        return bookId;
    }

    public void setBookId(long bookId) {
        this.bookId = bookId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
