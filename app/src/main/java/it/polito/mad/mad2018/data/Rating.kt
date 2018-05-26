package it.polito.mad.mad2018.data

class Rating() {
    var score: Float? = null
    var comment: String? = null
    var bookId: String? = null
    var timestamp: Long? = null

    constructor(score: Float, comment: String) : this() {
        this.score = score
        this.comment = comment
    }
}