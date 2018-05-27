const functions = require('firebase-functions');

const admin = require('firebase-admin');
admin.initializeApp();

const MAX_NOTIFICATION_LEN = 256;
const MAX_BOOK_TITLE_LEN = 64;

exports.onNewMessage = functions.database.ref('/conversations/{cid}/messages/{mid}')
    .onCreate((snapshot, context) => {
        const cid = context.params.cid;
        const rid = snapshot.child('recipient').val();
        const timestamp = snapshot.child('timestamp').val() * (-1);

        function performActions(result) {

            const uid = result.child('uid').val();

            let promises = [];
            promises.push(admin.database().ref('/users/' + uid + '/conversations/active/' + cid + '/timestamp').set(timestamp));

            if (uid === rid) {
                promises.push(result.child('unreadMessages').ref.transaction(count => {
                    return (count || 0) + 1;
                }));
            } else {
                let message = snapshot.child('text').val();
                if (message.length > MAX_NOTIFICATION_LEN) {
                    message = message.substring(0, MAX_NOTIFICATION_LEN - 3) + '...';
                }
                promises.push(sendNotifications(cid, uid, rid, message));
            }

            return Promise.all(promises);
        }

        let promises = [];

        promises.push(admin.database().ref('/conversations/' + cid + '/owner').once('value').then(result => {
            return performActions(result);
        }));
        promises.push(admin.database().ref('/conversations/' + cid + '/peer').once('value').then(result => {
            return performActions(result);
        }));

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

/* Compatibility with Lab04 */
exports.onBookDeletedOld = functions.database.ref('/books/{bid}/deleted')
    .onWrite((change, context) => {

        if (!(change.before.val() !== true && change.after.val() === true)) {
            return true;
        }

        return admin.database.ref('/books/{bid}/flags/deleted').set(true);
    });

exports.onBookDeleted = functions.database.ref('/books/{bid}/flags/deleted')
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

exports.onBorrowingStateChanged = functions.database.ref('/conversations/{cid}/flags/borrowingState')
    .onWrite((change, context) => {

        const cid = context.params.cid;

        if (change.after.val() === 2) {
            return admin.database().ref('/conversations/' + cid + '/bookId').once('value').then(bookId => {
                return admin.database().ref('/books/' + bookId.val() + '/flags/available').once('value').then(result => {
                    return result.val() === false ? sendMessage(cid, 'peer', 'bookNotAvailable') : true;
                });
            });
        }

        if (change.after.val() === 3) {
            let promises = [];

            promises.push(admin.database().ref('/conversations/' + cid + '/bookId').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/owner/uid').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/peer/uid').once('value'));

            return Promise.all(promises).then(results => {
                const bookId = results[0].val();
                const userId = results[1].val();
                const peerId = results[2].val();

                let promises = [];

                promises.push(sendMessage(cid, 'peer', 'borrowingRequestAccepted'));

                promises.push(admin.database().ref('/books/' + bookId + '/flags/available').set(false));
                promises.push(admin.database().ref('/users/' + userId + '/books/lentBooks/' + bookId).set(peerId));
                promises.push(admin.database().ref('/users/' + peerId + '/books/borrowedBooks/' + bookId).set(userId));

                promises.push(admin.database().ref('/users/' + userId + '/statistics/lentBooks').transaction(count => { return (count || 0) + 1; }));
                promises.push(admin.database().ref('/users/' + peerId + '/statistics/borrowedBooks').transaction(count => { return (count || 0) + 1; }));
                promises.push(admin.database().ref('/users/' + peerId + '/statistics/toBeReturnedBooks').transaction(count => { return (count || 0) + 1; }));

                return Promise.all(promises);
            });
        }

        return true;
    });

exports.onReturnStateChanged = functions.database.ref('/conversations/{cid}/flags/returnState')
        .onWrite((change, context) => {

            if (change.after.val() !== 3) {
                return true;
            }

            const cid = context.params.cid;
            let promises = [];

            promises.push(admin.database().ref('/conversations/' + cid + '/bookId').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/owner/uid').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/peer/uid').once('value'));

            return Promise.all(promises).then(results => {
                const bookId = results[0].val();
                const userId = results[1].val();
                const peerId = results[2].val();

                let promises = [];
                promises.push(admin.database().ref('/books/' + bookId + '/flags/available').set(true));
                promises.push(admin.database().ref('/users/' + userId + '/books/lentBooks/' + bookId).remove());
                promises.push(admin.database().ref('/users/' + peerId + '/books/borrowedBooks/' + bookId).remove());
                promises.push(admin.database().ref('/users/' + peerId + '/statistics/toBeReturnedBooks').transaction(count => { return count - 1; }));
                return Promise.all(promises);
            });
        });

exports.onUserRatingAdded = functions.database.ref('/conversations/{cid}/owner/rating')
        .onWrite((change, context) => {

            const cid = context.params.cid;

            let promises = [];
            promises.push(admin.database().ref('/conversations/' + cid + '/bookId').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/peer/uid').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/flags/ownerFeedback').set(true));
            return Promise.all(promises).then(results => { return onRatingAdded(results, change.after.val()) });
        });

exports.onPeerRatingAdded = functions.database.ref('/conversations/{cid}/peer/rating')
        .onWrite((change, context) => {

            const cid = context.params.cid;

            let promises = [];
            promises.push(admin.database().ref('/conversations/' + cid + '/bookId').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/owner/uid').once('value'));
            promises.push(admin.database().ref('/conversations/' + cid + '/flags/peerFeedback').set(true));
            return Promise.all(promises).then(results => { return onRatingAdded(results, change.after.val()) });
         });

exports.onBookAvailabilityChanged = functions.database.ref('/books/{bid}/flags')
        .onWrite((change, context) => {

            if (change.after.child('deleted').val() === true) {
                return true;
            }

            const bid = context.params.bid;
            const messageId = change.after.child('available').val() === true ? 'bookAvailable' : 'bookNotAvailable';

            return admin.database().ref('/conversations/').orderByChild('bookId').equalTo(bid)
                         .once('value').then(results => {

                     let promises = [];
                     results.forEach(conversation => {
                        if (conversation.child('flags/archived').val() !== true &&
                                conversation.child('flags/borrowingState').val() === 2) {
                            promises.push(sendMessage(conversation.key, 'peer', messageId));
                        }
                     });
                     return Promise.all(promises);
                 });
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
        promises.push(sendMessage(conversation.key, 'peer', 'bookDeleted'));
    });
    return Promise.all(promises);
}

function onRatingAdded(results, rating) {

    const timestamp = Date.now();
    const bookId = results[0].val();
    const uid = results[1].val();

    rating['bookId'] = bookId;
    rating['timestamp'] = timestamp;

    let promises = [];

    promises.push(admin.database().ref('/users/' + uid + '/ratings/' + (-timestamp)).set(rating));
    promises.push(admin.database().ref('/users/' + uid + '/statistics/ratingTotal')
        .transaction(total => { return (total || 0) + rating['score']; }));
    promises.push(admin.database().ref('/users/' + uid + '/statistics/ratingCount')
        .transaction(count => { return (count || 0) + 1; }));

    return Promise.all(promises);
}

function sendMessage(cid, recipient, textId) {

    const texts = {
        en: {
            borrowingRequestAccepted: "Your borrowing request has been accepted!",
            bookNotAvailable: "The requested book is currently not available",
            bookAvailable: "The requested book is again available",
            bookDeleted: "The book has been deleted by the owner",
        },
        it: {
            borrowingRequestAccepted: "La tua richiesta di prestito è stata accettata!",
            bookNotAvailable: "Il libro richiesto non è attualmente disponibile",
            bookAvailable: "Il libro richiesto è nuovamente disponibile",
            bookDeleted: "Il libro è stato eliminato dal proprietario",
        }
    }

    let promises = []

    promises.push(admin.database().ref('/conversations/' + cid + '/' + recipient + '/uid').once('value'));
    promises.push(admin.database().ref('/conversations/' + cid + '/language').once('value'));

    return Promise.all(promises).then(results => {
        const uid = results[0].val();
        const language = results[1].val();

        let message = new Object();
        message.recipient = uid;
        message.timestamp = Date.now();
        message.special = true;

        switch (language) {
            case 'it':
            case 'en':
                message.text = texts[language][textId];
                break;
            default:
                message.text = texts['en'][textId];
        }

        return admin.database().ref('/conversations/' + cid + '/messages').push().set(message);
    });
}
