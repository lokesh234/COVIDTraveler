package edu.wpi.cs528finalproject

// From https://stackoverflow.com/a/39561350/6465849
object FirebaseEncoder {
    fun encodeForFirebaseKey(s: String): String {
        return s
            .replace("_", "__")
            .replace(".", "_P")
            .replace("$", "_D")
            .replace("#", "_H")
            .replace("[", "_O")
            .replace("]", "_C")
            .replace("/", "_S")
    }

    fun decodeFromFirebaseKey(s: String): String {
        var i = 0
        var ni: Int
        var res = ""
        while (s.indexOf("_", i).also { ni = it } != -1) {
            res += s.substring(i, ni)
            if (ni + 1 < s.length) {
                when (s[ni + 1]) {
                    '_' -> {
                        res += '_'
                    }
                    'P' -> {
                        res += '.'
                    }
                    'D' -> {
                        res += '$'
                    }
                    'H' -> {
                        res += '#'
                    }
                    'O' -> {
                        res += '['
                    }
                    'C' -> {
                        res += ']'
                    }
                    'S' -> {
                        res += '/'
                    }
                }
                i = ni + 2
            } else {
                // this case is due to bad encoding
                break
            }
        }
        res += s.substring(i)
        return res
    }
}