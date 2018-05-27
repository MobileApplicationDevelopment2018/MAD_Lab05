package it.polito.mad.mad2018.chat;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.ObservableSnapshotArray;

import it.polito.mad.mad2018.R;
import it.polito.mad.mad2018.data.Conversation;

public class SingleChatAdapter extends FirebaseRecyclerAdapter<Conversation.Message, SingleChatAdapter.ChatHolder> {

    private final static int LAYOUT_MESSAGE_RIGHT = 0;
    private final static int LAYOUT_MESSAGE_LEFT = 1;
    private final static int LAYOUT_MESSAGE_SPECIAL = 2;


    private final OnItemCountChangedListener onItemCountChangedListener;
    private final ObservableSnapshotArray<Conversation.Message> conversation;

    SingleChatAdapter(@NonNull FirebaseRecyclerOptions<Conversation.Message> options,
                      @NonNull OnItemCountChangedListener onItemCountChangedListener) {

        super(options);
        this.conversation = options.getSnapshots();
        this.onItemCountChangedListener = onItemCountChangedListener;
    }

    @NonNull
    @Override
    public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate((viewType == LAYOUT_MESSAGE_SPECIAL
                                ? R.layout.item_message_special
                                : viewType == LAYOUT_MESSAGE_RIGHT
                                ? R.layout.item_message_right
                                : R.layout.item_message_left)
                        , parent, false);
        return new SingleChatAdapter.ChatHolder(view);
    }

    @Override
    protected void onBindViewHolder(@NonNull ChatHolder holder, int position, @NonNull Conversation.Message model) {
        holder.update(model);
    }

    @Override
    public int getItemViewType(int position) {
        return this.conversation.get(position).isSpecial() ?
                LAYOUT_MESSAGE_SPECIAL :
                this.conversation.get(position).isRecipient()
                        ? LAYOUT_MESSAGE_LEFT : LAYOUT_MESSAGE_RIGHT;
    }

    @Override
    public void onDataChanged() {
        super.onDataChanged();
        onItemCountChangedListener.onCountChangedListener(this.getItemCount());
    }

    interface OnItemCountChangedListener {
        void onCountChangedListener(int count);
    }

    static class ChatHolder extends RecyclerView.ViewHolder {
        private final TextView message;
        private final TextView dateTime;

        ChatHolder(View view) {
            super(view);
            this.message = view.findViewById(R.id.chat_user_text);
            this.dateTime = view.findViewById(R.id.chat_date_time);
        }

        private void update(Conversation.Message model) {
            this.message.setText(model.getText());
            this.dateTime.setText(model.getDateTime());
        }
    }
}
