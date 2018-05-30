package it.polito.mad.mad2018.chat

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RatingBar
import it.polito.mad.mad2018.R
import it.polito.mad.mad2018.data.Conversation
import it.polito.mad.mad2018.data.Rating
import it.polito.mad.mad2018.utils.Utilities

class RatingFragment : DialogFragment() {
    private lateinit var conversation: Conversation

    companion object Factory {

        const val TAG = "RatingFragment"

        fun newInstance(conversation: Conversation): RatingFragment {
            val ratingFragment = RatingFragment()
            ratingFragment.arguments = Bundle().apply {
                putSerializable(Conversation.CONVERSATION_KEY, conversation)
            }
            return ratingFragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        conversation = arguments!!.getSerializable(Conversation.CONVERSATION_KEY) as Conversation

        val builder: AlertDialog.Builder = AlertDialog.Builder(context!!)
        val inflater: LayoutInflater = activity!!.layoutInflater

        val view = inflater.inflate(R.layout.fragment_rating, null)

        builder.setView(view)
        builder.setPositiveButton(R.string.rate, { dialog, _ ->
            uploadRating()
            dialog.dismiss()
        })
        builder.setNegativeButton(android.R.string.cancel, { dialog, _ -> dialog.dismiss() })

        return builder.create()
    }

    private fun uploadRating() {
        val ratingBar = dialog.findViewById<RatingBar>(R.id.rating_bar)
        val ratingComment = Utilities.trimString(dialog.findViewById<EditText>(R.id.rating_comment).text.toString())
        conversation.uploadRating(Rating(ratingBar.rating, ratingComment))
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        (parentFragment as? OnDismissListener)?.onDialogDismiss()
    }

    interface OnDismissListener {
        fun onDialogDismiss()
    }
}
