package it.polito.mad.mad2018.profile

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.View
import com.firebase.ui.database.FirebaseRecyclerAdapter
import it.polito.mad.mad2018.R
import it.polito.mad.mad2018.data.LocalUserProfile
import it.polito.mad.mad2018.data.Rating
import it.polito.mad.mad2018.data.UserProfile

class ShowRatingsActivity : AppCompatActivity() {

    private lateinit var profile: UserProfile
    private lateinit var adapter: FirebaseRecyclerAdapter<Rating, RatingAdapter.RatingHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_ratings)

        profile = if (savedInstanceState != null) {
            savedInstanceState.getSerializable(UserProfile.PROFILE_INFO_KEY) as UserProfile
        } else {
            intent.getSerializableExtra(UserProfile.PROFILE_INFO_KEY) as UserProfile
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        title = (if (profile is LocalUserProfile)
            getString(R.string.my_ratings_title)
        else
            getString(R.string.ratings_title, profile.username))

        val loadingView = findViewById<View>(R.id.sr_loading)
        val noRatingsView = findViewById<View>(R.id.sr_no_ratings)
        val recyclerView = findViewById<RecyclerView>(R.id.sr_ratings)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val options = profile.ratingsReferences
        adapter = RatingAdapter(options, { count: Int ->
            loadingView.visibility = View.GONE
            noRatingsView.visibility = if (count == 0) View.VISIBLE else View.GONE
            recyclerView.visibility = if (count == 0) View.GONE else View.VISIBLE
        })

        recyclerView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        adapter.startListening()
    }

    override fun onStop() {
        adapter.stopListening()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putSerializable(UserProfile.PROFILE_INFO_KEY, profile)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
