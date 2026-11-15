import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteValidator as V

fun main() {
    val good = listOf(
        Note(), Note(title = "Текст 😀", body = "Строка\nВторая", tags = "abc-10 x"),
        Note(title = "a".repeat(100), body = "b".repeat(5000), tags = "aaa bbb ccc ddd eee"),
        Note(title = "😀".repeat(100), tags = "a-1"),
    )
    val bad = listOf(
        Note(title = "a".repeat(101)), Note(title="a\nb"), Note(title="a\r"),
        Note(body = "z".repeat(5001)), Note(body = "NUL\u0000x"), Note(tags = "тег"),
        Note(tags = "FOO"), Note(tags="ab_cd"), Note(tags="a  b"), Note(tags=" a"),
        Note(tags="a b c d e f"), Note(tags="a".repeat(21)),
    )
    good.forEachIndexed { i, n -> check(V.validate(n) == null) { "good[$i]: ${V.validate(n)}" } }
    bad.forEachIndexed { i, n -> check(V.validate(n) != null) { "bad[$i]" } }
    check(V.tagsError("a ") == null)
    check(V.tagsError("a".repeat(20)) == null)
    check(V.titleError("😀".repeat(101)) != null)
    println("PASS Kotlin validator smoke: ${good.size} valid + ${bad.size} invalid + 3 edge cases")
}
