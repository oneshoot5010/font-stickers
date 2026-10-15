package com.fontcopy.app;

final class Fonts {
    private Fonts() {}

    static final String SC = "ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";

    static String conv(int style, String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) sb.append(map(style, s.charAt(i)));
        return sb.toString();
    }

    private static String off(char c, int up, int low, int dig) {
        if (c >= 'A' && c <= 'Z') return new String(Character.toChars(up + (c - 'A')));
        if (c >= 'a' && c <= 'z') return new String(Character.toChars(low + (c - 'a')));
        if (dig > 0 && c >= '0' && c <= '9') return new String(Character.toChars(dig + (c - '0')));
        return String.valueOf(c);
    }

    private static String map(int st, char c) {
        switch (st) {
            case 1: return off(c, 0x1D4D0, 0x1D4EA, 0x1D7CE);
            case 2: {
                switch (c) {
                    case 'C': return "ℂ";
                    case 'H': return "ℍ";
                    case 'N': return "ℕ";
                    case 'P': return "ℙ";
                    case 'Q': return "ℚ";
                    case 'R': return "ℝ";
                    case 'Z': return "ℤ";
                    default: return off(c, 0x1D538, 0x1D552, 0x1D7D8);
                }
            }
            case 3: return off(c, 0x1D56C, 0x1D586, 0x1D7CE);
            case 4: return off(c, 0x1D400, 0x1D41A, 0x1D7CE);
            case 5: return off(c, 0x1D670, 0x1D68A, 0x1D7F6);
            case 6: {
                char l = Character.toLowerCase(c);
                if (l >= 'a' && l <= 'z') return String.valueOf(SC.charAt(l - 'a'));
                return String.valueOf(c);
            }
            case 7: {
                if (c >= 'A' && c <= 'Z') return String.valueOf((char) (0x24B6 + (c - 'A')));
                if (c >= 'a' && c <= 'z') return String.valueOf((char) (0x24D0 + (c - 'a')));
                if (c >= '1' && c <= '9') return String.valueOf((char) (0x2460 + (c - '1')));
                if (c == '0') return "⓪";
                return String.valueOf(c);
            }
            case 8: {
                if (c == ' ') return "\u3000";
                if (c > 32 && c < 127) return String.valueOf((char) (c + 0xFEE0));
                return String.valueOf(c);
            }
            default: return String.valueOf(c);
        }
    }
}
