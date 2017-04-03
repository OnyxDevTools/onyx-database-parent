package com.onyxdevtools.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by tosborn1 on 4/2/17.
 *
 * Simple POJO for a Cookbook recipee
 */
@Entity
public class Recipe extends ManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    private long recipeId;

    @Attribute
    private String content;

    @Attribute
    private String details;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
                  inverse = "recipes",
                  inverseClass = CookBook.class)
    private CookBook cookBook;

    public long getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(long recipeId) {
        this.recipeId = recipeId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public CookBook getCookBook() {
        return cookBook;
    }

    public void setCookBook(CookBook cookBook) {
        this.cookBook = cookBook;
    }
}
