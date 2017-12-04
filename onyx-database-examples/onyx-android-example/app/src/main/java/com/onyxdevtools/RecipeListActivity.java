package com.onyxdevtools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.onyx.exception.InitializationException;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.entities.CookBook;
import com.onyxdevtools.entities.Recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity representing a list of Recipes. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link RecipeDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class RecipeListActivity extends AppCompatActivity {

    static PersistenceManager sPersistenceManager;
    private static PersistenceManagerFactory sPersistenceManagerFactory;

    private static final String TAG = "RecipeListActivity";

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_list);

        // Create and connect to the persistence manager factory.  In this case, it is a Cache Manager Factory
        // but, it can also be an embedded or remote persistence manager factory
        if(sPersistenceManagerFactory == null) {
            sPersistenceManagerFactory = new EmbeddedPersistenceManagerFactory(getApplicationContext().getFilesDir().getPath() + File.separator + "test2.onx");
            try {
                long before = System.currentTimeMillis();
                sPersistenceManagerFactory.initialize();
                long after = System.currentTimeMillis();
                Log.e(TAG, "Took " + (after - before));
            } catch (InitializationException e) {
                Log.e(TAG, "Cannot initialize Persistence Manager Factory");
            }

            sPersistenceManager = sPersistenceManagerFactory.getPersistenceManager();
            insertTestData();
        }


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        View recyclerView = findViewById(R.id.recipe_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        if (findViewById(R.id.recipe_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }
    }

    private void insertTestData()
    {
        final CookBook cookBook = new CookBook();
        cookBook.setTitle("Onyx's House of Yummy");

        final Recipe cupOfNoodle = new Recipe();
        cupOfNoodle.setContent("Cup of Noodle");
        cupOfNoodle.setDetails("Open lid, add water, put powder stuff in and microwave until it boils over and makes a mess in your microwave.");

        final Recipe pbAndJ = new Recipe();
        pbAndJ.setContent("Peanut Butter Sandwich");
        pbAndJ.setDetails("Not the best sandwich but something that requires little effort.  Get bread, spread peanut butter and jelly on.  Let butter knife sit in sink until it starts growing stuff on it.");

        final Recipe meatloaf = new Recipe();
        meatloaf.setContent("Meatloaf");
        meatloaf.setDetails("No idea how this is made.  Put Catsup on it though when ready to eat.");

        final Recipe frozenBurrito = new Recipe();
        frozenBurrito.setContent("Frozen Burrito");
        frozenBurrito.setDetails("Microwave until it is ready to scold your tongue.");

        final Recipe spaghetti = new Recipe();
        spaghetti.setContent("Spaghetti");
        spaghetti.setDetails("Boil noodles and put red stuff in when noodles stick to your cabinet.");

        final Recipe tacoBell = new Recipe();
        tacoBell.setContent("Taco Bell");
        tacoBell.setDetails("Drive up, order, pay, shove in face as you drive away.");

        final Recipe water = new Recipe();
        water.setContent("Water");
        water.setDetails("Pour, Chug, Pee 1 hour later");

        final Recipe hotDog = new Recipe();
        hotDog.setContent("Hot Dog");
        hotDog.setDetails("Unsure, I think you can boil, microwave, grill or bake.  Is it safe to eat raw???  They are a bit of a mystery.");

        final Recipe lasagna = new Recipe();
        lasagna.setContent("Lasagna");
        lasagna.setDetails("Beg spouse to make, ask spouse every 5 minutes if it is done yet, complain about how hungry you are, and enjoy!  This could be the last lasagna you ever get to eat.");

        List<Recipe> recipes = new ArrayList<>();
        recipes.add(cupOfNoodle);
        recipes.add(pbAndJ);
        recipes.add(meatloaf);
        recipes.add(spaghetti);
        recipes.add(frozenBurrito);
        recipes.add(tacoBell);
        recipes.add(water);
        recipes.add(hotDog);
        recipes.add(lasagna);

        cookBook.setRecipes(recipes);

        try {
            sPersistenceManager.saveEntity(cookBook);
        } catch (OnyxException e) {
            Log.e(TAG, "Could not persist cookbook");
        }
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        CookBook cookBook = null;
        try {
            cookBook = sPersistenceManager.findById(CookBook.class, 1L);
        } catch (OnyxException e) {
            Log.e(TAG, "Could not query cook books");
        }

        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(cookBook));
    }

    class SimpleItemRecyclerViewAdapter extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final CookBook mCookBook;

        SimpleItemRecyclerViewAdapter(CookBook cookBook) {
            mCookBook = cookBook;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recipe_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mCookBook.getRecipes().get(position);
            holder.mIdView.setText(String.valueOf(holder.mItem.getRecipeId()));
            holder.mContentView.setText(holder.mItem.getContent());

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putLong(RecipeDetailFragment.ARG_ITEM_ID, holder.mItem.getRecipeId());
                        RecipeDetailFragment fragment = new RecipeDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.recipe_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = view.getContext();
                        Intent intent = new Intent(context, RecipeDetailActivity.class);
                        intent.putExtra(RecipeDetailFragment.ARG_ITEM_ID, holder.mItem.getRecipeId());

                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mCookBook.getRecipes().size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View mView;
            final TextView mIdView;
            final TextView mContentView;
            Recipe mItem;

            ViewHolder(View view) {
                super(view);
                mView = view;
                mIdView = view.findViewById(R.id.id);
                mContentView = view.findViewById(R.id.content);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + mContentView.getText() + "'";
            }
        }
    }
}
