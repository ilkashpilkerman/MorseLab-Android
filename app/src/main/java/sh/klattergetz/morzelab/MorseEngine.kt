package sh.klattergetz.morzelab

/** International Morse; space (between letters) is a timing event, not a tree edge. */
object MorseEngine {
    val codes = linkedMapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
    )
    // Russian Morse alphabet. Ё shares the code of Е (ITU practice).
    // Keep Ё in the dictionary for reference; decoder resolves that code to Е.
    val russianCodes = linkedMapOf(
        'А' to ".-", 'Б' to "-...", 'В' to ".--", 'Г' to "--.",
        'Д' to "-..", 'Е' to ".", 'Ё' to ".", 'Ж' to "...-",
        'З' to "--..", 'И' to "..", 'Й' to ".---", 'К' to "-.-",
        'Л' to ".-..", 'М' to "--", 'Н' to "-.", 'О' to "---",
        'П' to ".--.", 'Р' to ".-.", 'С' to "...", 'Т' to "-",
        'У' to "..-", 'Ф' to "..-.", 'Х' to "....", 'Ц' to "-.-.",
        'Ч' to "---.", 'Ш' to "----", 'Щ' to "--.-", 'Ъ' to "--.--",
        'Ы' to "-.--", 'Ь' to "-..-", 'Э' to "..-..", 'Ю' to "..--",
        'Я' to ".-.-", '0' to "-----", '1' to ".----",
        '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----."
    )
    val byCode = codes.entries.associate { it.value to it.key }
    val russianByCode = russianCodes.entries.associate { it.value to it.key }.toMutableMap().apply {
        put(".", 'Е')
    }
    fun decode(code: String, russian: Boolean = false): Char? =
        if (russian) russianByCode[code] else byCode[code]
}
