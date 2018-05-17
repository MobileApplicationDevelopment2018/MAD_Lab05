package it.polito.mad.mad2018.library;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Book;
import it.polito.mad.mad2018.utils.GlideApp;

class BookAdapter extends FirebaseRecyclerAdapter<Book, BookAdapter.BookHolder> {
    private final OnItemClickListener onItemClickListener;
    private final OnItemCountChangedListener onItemCountChangedListener;

    BookAdapter(@NonNull FirebaseRecyclerOptions<Book> options,
                @NonNull OnItemClickListener onItemClickListener,
                @NonNull OnItemCountChangedListener onItemCountChangedListener) {
        super(options);
        this.onItemClickListener = onItemClickListener;
        this.onItemCountChangedListener = onItemCountChangedListener;
    }

    @NonNull
    @Override
    public BookHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new BookHolder(view, onItemClickListener);
    }

    @Override
    protected void onBindViewHolder(@NonNull BookHolder holder, int position, @NonNull Book model) {
        holder.update(model);
    }

    @Override
    public void onDataChanged() {
        super.onDataChanged();
        onItemCountChangedListener.onCountChangedListener(this.getItemCount());
    }

    interface OnItemClickListener {
        void onClick(View view, Book book);
    }

    interface OnItemCountChangedListener {
        void onCountChangedListener(int count);
    }

    static class BookHolder extends RecyclerView.ViewHolder {

        private final Context context;
        private final TextView bookTitle;
        private final TextView bookAuthors;
        private final ImageView bookPicture;

        private Book model;

        private BookHolder(@NonNull View view, @NonNull OnItemClickListener listener) {
            super(view);

            this.context = view.getContext();
            this.bookTitle = view.findViewById(R.id.fbs_book_item_title);
            this.bookAuthors = view.findViewById(R.id.fbs_book_item_author);
            this.bookPicture = view.findViewById(R.id.fbs_book_item_image);

            view.setOnClickListener(v -> listener.onClick(v, model));
        }

        private void update(Book model) {
            this.model = model;
            bookTitle.setText(model.getTitle());
            bookAuthors.setText(model.getAuthors(", "));

            GlideApp.with(context)
                    .load(model.getBookThumbnailReferenceOrNull())
                    .placeholder(R.drawable.ic_default_book_preview)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(bookPicture);
        }
    }
}