package it.polito.mad.mad2018.chat

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RatingBar
import com.google.firebase.database.FirebaseDatabase
import it.polito.mad.mad2018.R

//import kotlinx.android.synthetic.main.fragment_rating.*

class RatingFragment : DialogFragment() {

    private val FIREBASE_CONVERSATIONS_KEY = "conversations"
    private val FIREBASE_OWNER_KEY = "owner"
    private val FIREBASE_PEER_KEY = "peer"
    private val FIREBASE_RATING_KEY = "rating"

    private lateinit var conversationId: String
    private var isOwner: Boolean = false

    companion object Factory {
        fun newInstance(isOwner: Boolean, conversationId: String): RatingFragment {
            val ratingFragment = RatingFragment()
            ratingFragment.isOwner = isOwner
            ratingFragment.conversationId = conversationId
            return ratingFragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        val builder: AlertDialog.Builder = AlertDialog.Builder(context!!)
        val inflater: LayoutInflater = activity!!.layoutInflater

        val view = inflater.inflate(R.layout.fragment_rating, null)

        builder.setView(view)
        builder.setPositiveButton("Rate", { dialog, _ ->
            uploadRating()
            dialog.dismiss()
        })
        builder.setNegativeButton("Cancel", { dialog, _ -> dialog.dismiss() })

        return builder.create()
    }

    private fun uploadRating() {

        val ratingBar = dialog.findViewById<RatingBar>(R.id.rating_bar)
        val ratingComment = dialog.findViewById<EditText>(R.id.rating_comment)

        FirebaseDatabase.getInstance().reference.child(FIREBASE_CONVERSATIONS_KEY)
                .child(conversationId)
                .child(if (isOwner) FIREBASE_OWNER_KEY else FIREBASE_PEER_KEY)
                .child(FIREBASE_RATING_KEY)
                .setValue(Rating(ratingBar.rating, ratingComment.text.toString()))
    }

    class Rating(val score: Float, val comment: String)
}
