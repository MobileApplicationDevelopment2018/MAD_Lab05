const functions = require('firebase-functions');

const admin = require('firebase-admin');
admin.initializeApp();

const MAX_NOTIFICATION_LEN = 256;
const MAX_BOOK_TITLE_LEN = 64;

exports.onNewMessage = functions.database.ref('/conversations/{cid}/messages/{mid}')
    .onCreate((snapshot, context) => {
        const cid = context.params.cid;
        const uid = context.auth.uid;
        const rid = snapshot.child('recipient').val();
        const timestamp = snapshot.child('timestamp').val() * (-1);

        let promises = [];

        function updateUnreadMessages(result) {
            if (result.child('uid').val() === rid) {
                return result.child('unreadMessages').ref.transaction(count => {
                    return (count || 0) + 1;
                });
            }

            return true;
        }

        promises.push(admin.database().ref('/conversations/' + cid + '/owner').once('value').then(result => {
            return updateUnreadMessages(result);
        }));
        promises.push(admin.database().ref('/conversations/' + cid + '/peer').once('value').then(result => {
            return updateUnreadMessages(result);
        }));

        promises.push(admin.database().ref('/users/' + uid + '/conversations/active/' + cid + '/timestamp').set(timestamp));
        promises.push(admin.database().ref('/users/' + rid + '/conversations/active/' + cid + '/timestamp').set(timestamp));

        let message  = snapshot.child('text').val();
        if (message.length > MAX_NOTIFICATION_LEN) {
            message = message.substring(0, MAX_NOTIFICATION_LEN - 3) + '...';
        }
        promises.push(sendNotifications(cid, uid, rid, message));

        return Promise.all(promises);
    });

exports.onConversationArchived = functions.database.ref('/conversations/{cid}/flags/archived')
    .onWrite((change, context) => {

        if (!(change.before.val() !== true && change.after.val() === true)) {
            return true;
        }

        const cid = context.params.cid;
        let promises = [];

        promises.push(admin.database().ref('/conversations/' + cid + '/owner/uid').once('value').then(result => {
            return archive(result.val(), cid);
        }));
        promises.push(admin.database().ref('/conversations/' + cid + '/peer/uid').once('value').then(result => {
            return archive(result.val(), cid);
        }));

        return Promise.all(promises);
    });

exports.onBookDeleted = functions.database.ref('/books/{bid}/deleted')
    .onWrite((change, context) => {

        if (!(change.before.val() !== true && change.after.val() === true)) {
            return true;
        }

        const bid = context.params.bid;

        return admin.database().ref('/conversations/').orderByChild('bookId').equalTo(bid)
                .once('value').then(results => {
            return performDelete(results);
        })
    });

function sendNotifications(cid, uid, rid, message) {

    const tokensPromise = admin.database().ref('/tokens/' + rid).once('value');
    const senderNamePromise = admin.database().ref('/users/' + uid + '/profile/username').once('value');

    const bookTitlePromise = admin.database().ref('/conversations/' + cid + '/bookId').once('value').then(result => {
         return admin.database().ref('/books/' + result.val() + '/bookInfo/title').once('value');
    });

    let tokensSnapshot;
    let tokens;

    return Promise.all([tokensPromise, senderNamePromise, bookTitlePromise]).then(results => {

        tokensSnapshot = results[0];
        if (!tokensSnapshot.hasChildren()) {
            return true;
        }

        const senderName = results[1].val();
        let bookTitle = results[2].val();
            if (bookTitle.length > MAX_BOOK_TITLE_LEN) {
                bookTitle = bookTitle.substring(0, MAX_BOOK_TITLE_LEN - 3) + '...';
        }

        const payload = {
            data: {
                title: senderName + ' - ' + bookTitle,
                message: message,
                conversationId: cid
            }
        };

        tokens = Object.keys(tokensSnapshot.val());
        return admin.messaging().sendToDevice(tokens, payload);

        }).then(response => {

            if (!response || !response.results) {
                return true;
            }

            const tokensToRemove = [];
            response.results.forEach((result, index) => {
                const error = result.error;
                if (error) {
                    if (error.code === 'messaging/invalid-registration-token' ||
                            error.code === 'messaging/registration-token-not-registered') {
                        tokensToRemove.push(tokensSnapshot.ref.child(tokens[index]).remove());
                    }
                }
            });
            return Promise.all(tokensToRemove);

        });
}

function archive(uid, cid) {

    const activeRef = admin.database().ref('/users/' + uid + '/conversations/active/' + cid);
    const archivedRef = admin.database().ref('/users/' + uid + '/conversations/archived/' + cid);

    return activeRef.once('value').then(result => {
        if (result.val() === null || result.val() === undefined) {
            return true;
        }

        let promises = [];
        promises.push(activeRef.remove());
        promises.push(archivedRef.set(result.val()));
        return Promise.all(promises);
    });
}

function performDelete(conversations) {
    let promises = [];
    conversations.forEach(conversation => {
        promises.push(conversation.ref.child('flags/bookDeleted').set(true));
        promises.push(conversation.ref.child('flags/archived').set(true));
    });
    return Promise.all(promises);
}
