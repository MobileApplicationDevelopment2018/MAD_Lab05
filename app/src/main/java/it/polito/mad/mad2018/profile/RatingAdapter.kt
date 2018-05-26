package it.polito.mad.mad2018.profile

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import it.polito.mad.mad2018.R
import it.polito.mad.mad2018.data.Rating

internal class RatingAdapter(options: FirebaseRecyclerOptions<Rating>,
                             private val onItemCountChangedListener: (Int) -> Unit)
    : FirebaseRecyclerAdapter<Rating, RatingAdapter.RatingHolder>(options) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatingHolder {
        return RatingHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rating, parent, false))
    }

    override fun onBindViewHolder(holder: RatingHolder, position: Int, model: Rating) {
        holder.update(model)
    }

    override fun onDataChanged() {
        super.onDataChanged()
        onItemCountChangedListener(this.itemCount)
    }

    internal class RatingHolder constructor(view: View)
        : RecyclerView.ViewHolder(view) {

        @Suppress("DEPRECATION")
        private val locale = view.context.resources.configuration.locale
        private val ratingScore = view.findViewById<TextView>(R.id.rtg_score)
        private val ratingComment = view.findViewById<TextView>(R.id.rtg_comment)

        internal fun update(model: Rating) {
            ratingScore.text = String.format(locale, "%.1f/5", model.score)
            ratingComment.text = model.comment
        }
    }
}