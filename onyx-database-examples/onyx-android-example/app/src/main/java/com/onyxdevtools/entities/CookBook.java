package com.onyxdevtools.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

/**
 * Created by Tim Osborn on 4/2/17.
 *
 * Simple entity for a cookbook.
 */
@SuppressWarnings("unused")
@Entity
public class CookBook extends ManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    private long cookBookId;

    @Attribute
    private String title;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
                  cascadePolicy = CascadePolicy.SAVE,
                  inverse = "cookBook",
                  inverseClass = Recipe.class,
                  fetchPolicy = FetchPolicy.LAZY)
    private List<Recipe> recipes;

    public long getCookBookId() {
        return cookBookId;
    }

    public void setCookBookId(long cookBookId) {
        this.cookBookId = cookBookId;
    }

    public String getTitle() {
        return title;
    }

    @SuppressWarnings("SameParameterValue")
    public void setTitle(String title) {
        this.title = title;
    }

    public List<Recipe> getRecipes() {
        return recipes;
    }

    public void setRecipes(List<Recipe> recipes) {
        this.recipes = recipes;
    }
}
