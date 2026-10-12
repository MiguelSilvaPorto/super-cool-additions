package com.sccadd.requester;

/**
 * Amount expressions for the requester: {@code + - * / x ( )}, decimals and the suffixes
 * {@code k} (thousand), {@code m} (million), {@code g} (billion) and {@code t} (trillion).
 * "64*2/4" is 32, "1.5m" is 1,500,000, "2g" is 2,000,000,000.
 */
public final class BigMath {
    public static final long INVALID = Long.MIN_VALUE;
    public static final long MAX = 9_000_000_000_000_000L;

    private BigMath() {
    }

    public static boolean allowedChar(char c) {
        return c >= '0' && c <= '9' || "+-*/xX() .kKmMgGtT".indexOf(c) >= 0;
    }

    public static long eval(String text) {
        return evalWith(text, "kmgt", new double[]{1e3, 1e6, 1e9, 1e12});
    }

    /** A time: plain numbers are seconds; s, m, h and d are seconds, minutes, hours and days ("1.5h", "90", "2m30s"). */
    public static long evalSeconds(String text) {
        return evalWith(text, "smhd", new double[]{1.0, 60.0, 3600.0, 86400.0});
    }

    public static boolean allowedTimeChar(char c) {
        return c >= '0' && c <= '9' || "+-*/()x. sSmMhHdD".indexOf(c) >= 0;
    }

    /** 90 -> "1.5m", 3600 -> "1h". */
    public static String compactTime(long seconds) {
        if (seconds < 60L) {
            return seconds + "s";
        }
        if (seconds < 3600L) {
            return trim(seconds / 60.0) + "m";
        }
        if (seconds < 86400L) {
            return trim(seconds / 3600.0) + "h";
        }
        return trim(seconds / 86400.0) + "d";
    }

    private static String trim(double value) {
        String digits = String.format("%.2f", value);
        return digits.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static long evalWith(String text, String suffixes, double[] multipliers) {
        if (text == null || text.isBlank()) {
            return INVALID;
        }
        try {
            Parser parser = new Parser(text.trim(), suffixes, multipliers);
            double value = parser.expr();
            parser.skip();
            if (parser.pos != parser.text.length() || Double.isNaN(value) || Double.isInfinite(value)) {
                return INVALID;
            }
            if (value >= MAX) {
                return MAX;
            }
            return value <= -MAX ? -MAX : Math.round(value);
        } catch (RuntimeException failed) {
            return INVALID;
        }
    }

    /** A compact form for narrow fields: 12,345,678 -> "12.3m". Exact below ten thousand. */
    public static String compact(long value) {
        if (value < 10_000L) {
            return Long.toString(value);
        }
        String[] names = {"t", "g", "m", "k"};
        long[] sizes = {1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L};
        for (int i = 0; i < names.length; i++) {
            if (value >= sizes[i]) {
                double scaled = (double) value / sizes[i];
                String digits = scaled >= 100 ? String.format("%.0f", scaled) : scaled >= 10 ? String.format("%.1f", scaled) : String.format("%.2f", scaled);
                if (digits.contains(".")) {
                    digits = digits.replaceAll("0+$", "").replaceAll("\\.$", "");
                }
                return digits + names[i];
            }
        }
        return Long.toString(value);
    }

    private static final class Parser {
        final String text;
        final String suffixes;
        final double[] multipliers;
        int pos;

        Parser(String text, String suffixes, double[] multipliers) {
            this.text = text;
            this.suffixes = suffixes;
            this.multipliers = multipliers;
        }

        void skip() {
            while (this.pos < this.text.length() && this.text.charAt(this.pos) == ' ') {
                this.pos++;
            }
        }

        double expr() {
            double value = this.term();
            while (true) {
                this.skip();
                if (this.pos >= this.text.length()) {
                    return value;
                }
                char c = this.text.charAt(this.pos);
                if (c == '+') {
                    this.pos++;
                    value += this.term();
                } else if (c == '-') {
                    this.pos++;
                    value -= this.term();
                } else {
                    return value;
                }
            }
        }

        double term() {
            double value = this.factor();
            while (true) {
                this.skip();
                if (this.pos >= this.text.length()) {
                    return value;
                }
                char c = this.text.charAt(this.pos);
                if (c == '*' || c == 'x' || c == 'X') {
                    this.pos++;
                    value *= this.factor();
                } else if (c == '/') {
                    this.pos++;
                    double divisor = this.factor();
                    if (divisor == 0.0) {
                        throw new ArithmeticException("div0");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        double factor() {
            this.skip();
            if (this.pos >= this.text.length()) {
                throw new IllegalStateException("end");
            }
            char c = this.text.charAt(this.pos);
            if (c == '+') {
                this.pos++;
                return this.factor();
            }
            if (c == '-') {
                this.pos++;
                return -this.factor();
            }
            if (c == '(') {
                this.pos++;
                double value = this.expr();
                this.skip();
                if (this.pos < this.text.length() && this.text.charAt(this.pos) == ')') {
                    this.pos++;
                    return value;
                }
                throw new IllegalStateException(")");
            }
            int start = this.pos;
            boolean dot = false;
            while (this.pos < this.text.length()) {
                char d = this.text.charAt(this.pos);
                if (d >= '0' && d <= '9') {
                    this.pos++;
                } else if (d == '.' && !dot) {
                    dot = true;
                    this.pos++;
                } else {
                    break;
                }
            }
            if (this.pos == start || this.pos - start > 24) {
                throw new IllegalStateException("number");
            }
            double value = Double.parseDouble(this.text.substring(start, this.pos));
            // A suffix is a number followed by a unit; times chain ("2m30s").
            while (this.pos < this.text.length()) {
                int at = this.suffixes.indexOf(Character.toLowerCase(this.text.charAt(this.pos)));
                if (at < 0) {
                    break;
                }
                value *= this.multipliers[at];
                this.pos++;
                // "2m30s": a number straight after a unit continues the same time.
                int next = this.pos;
                while (next < this.text.length() && this.text.charAt(next) >= '0' && this.text.charAt(next) <= '9') {
                    next++;
                }
                if (this.multipliers[0] == 1.0 && next > this.pos) {
                    int unit = next < this.text.length() ? this.suffixes.indexOf(Character.toLowerCase(this.text.charAt(next))) : -1;
                    if (unit >= 0) {
                        value += Double.parseDouble(this.text.substring(this.pos, next)) * this.multipliers[unit];
                        this.pos = next + 1;
                    }
                }
                break;
            }
            return value;
        }
    }
}
