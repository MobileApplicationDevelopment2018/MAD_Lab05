package it.polito.mad.mad2018.profile

import android.content.Intent
import android.support.v4.content.ContextCompat.startActivity
import android.support.v7.widget.RecyclerView
import android.text.format.DateFormat
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.firebase.ui.common.ChangeEventType
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import it.polito.mad.mad2018.R
import it.polito.mad.mad2018.data.Book
import it.polito.mad.mad2018.data.Rating
import it.polito.mad.mad2018.library.BookInfoActivity
import it.polito.mad.mad2018.library.BookInfoFragment
import it.polito.mad.mad2018.utils.Utilities
import kotlinx.android.synthetic.main.item_rating.view.*
import kotlin.collections.component1
import kotlin.collections.component2

internal class RatingAdapter(options: FirebaseRecyclerOptions<Rating>,
                             private val onItemCountChangedListener: (Int) -> Unit)
    : FirebaseRecyclerAdapter<Rating, RatingAdapter.RatingHolder>(options) {

    private val bookListeners = HashMap<String, Pair<ValueEventListener, MutableSet<Int>>>()
    private val books = HashMap<String, Book>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatingHolder {

        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rating, parent, false)
        return RatingHolder(view)
    }

    override fun onBindViewHolder(holder: RatingHolder, position: Int, model: Rating) {

        val bookId = model.bookId
        val book = books[bookId]

        if (book == null) {
            this.addListener(bookId!!, position)
        }

        holder.update(model, book)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        for ((key, value) in bookListeners) {
            Book.unsetOnBookLoadedListener(key, value.first)
        }
    }

    override fun onDataChanged() {
        super.onDataChanged()
        onItemCountChangedListener(this.itemCount)
    }

    override fun onChildChanged(type: ChangeEventType, snapshot: DataSnapshot, newIndex: Int, oldIndex: Int) {
        super.onChildChanged(type, snapshot, newIndex, oldIndex)

        if (type == ChangeEventType.MOVED && newIndex != oldIndex) {

            for (listener in bookListeners.values) {
                if (listener.second.contains(oldIndex)) {
                    listener.second.remove(oldIndex)
                    listener.second.add(newIndex)
                }
            }
        }
    }

    private fun addListener(id: String, position: Int) {

        var listenerPair = bookListeners[id]
        if (listenerPair == null) {
            val listener = setOnBookLoadedListener(id)
            listenerPair = Pair(listener, HashSet(listOf(position)))
            bookListeners[id] = listenerPair
        } else {
            listenerPair.second.add(position)
        }
    }

    private fun setOnBookLoadedListener(bookId: String): ValueEventListener {

        return Book.setOnBookLoadedListener(
                bookId,
                object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {

                        val listener = bookListeners.remove(bookId) ?: return
                        val data = dataSnapshot.getValue(Book.Data::class.java) ?: return

                        Book.unsetOnBookLoadedListener(bookId, listener.first)
                        books[bookId] = Book(bookId, data)
                        for (position in listener.second) {
                            notifyItemChanged(position)
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        val listener = bookListeners.remove(bookId) ?: return
                        Book.unsetOnBookLoadedListener(bookId, listener.first!!)
                    }
                })
    }

    internal class RatingHolder constructor(view: View)
        : RecyclerView.ViewHolder(view) {

        @Suppress("DEPRECATION")
        private val locale = view.context.resources.configuration.locale
        private var book: Book? = null

        internal fun update(model: Rating, book: Book?) {

            this.book = book

            itemView.rtg_score.text = String.format(locale, "%.1f/5.0", model.score)
            itemView.rtg_comment.text = if (Utilities.isNullOrWhitespace(model.comment))
                itemView.context.getString(R.string.no_comment)
            else
                model.comment

            itemView.rtg_book_title.text = if (book == null) itemView.context.getString(R.string.loading) else book.title
            itemView.rtg_show_book.visibility = if (book == null) View.INVISIBLE else View.VISIBLE

            itemView.rtg_date.text = DateFormat.getMediumDateFormat(itemView.context).format(model.timestamp)

            itemView.setOnClickListener {
                itemView.rtg_comment.maxLines = if (itemView.rtg_comment.maxLines == Int.MAX_VALUE) 2 else Int.MAX_VALUE
            }

            itemView.rtg_show_book.setOnClickListener {
                val toBookInfo = Intent(itemView.context, BookInfoActivity::class.java)
                toBookInfo.putExtra(Book.BOOK_KEY, book)
                toBookInfo.putExtra(BookInfoFragment.BOOK_SHOW_OWNER_KEY, true)
                startActivity(itemView.context, toBookInfo, null)
            }
        }
    }
}