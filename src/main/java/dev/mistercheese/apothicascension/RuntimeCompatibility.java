// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tiny allocation-bounded numeric version matcher used by diagnostics and qualification only.
 *
 * <p>Production gameplay does not poll this class.  Loader metadata is still the first-line gate;
 * this matcher exists so one runtime JAR can prove that the environment it was allowed to load in
 * is inside an audited compatibility band without linking a reflection/version-adapter framework
 * into hot paths.</p>
 */
public final class RuntimeCompatibility {

    public record VersionBand(String minimumInclusive, String maximumExclusive, boolean required) {
        public boolean contains(String actual) {
            if (actual == null || actual.isBlank()) return false;
            try {
                return compare(actual, minimumInclusive) >= 0 && compare(actual, maximumExclusive) < 0;
            }
            catch (ArithmeticException ex) {
                // Qualification must fail closed on a malformed/absurd version component rather
                // than aborting the entire self-test because integer parsing overflowed.
                return false;
            }
        }
    }

    private RuntimeCompatibility() {}

    public static int compare(String left, String right) {
        int[] a = numericParts(left);
        int[] b = numericParts(right);
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            int cmp = Integer.compare(av, bv);
            if (cmp != 0) return cmp;
        }
        return 0;
    }


    private static int[] numericParts(String version) {
        List<Integer> values = new ArrayList<>(4);
        int value = -1;
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (c >= '0' && c <= '9') {
                if (value < 0) value = c - '0';
                else value = Math.addExact(Math.multiplyExact(value, 10), c - '0');
            }
            else if (value >= 0) {
                values.add(value);
                value = -1;
            }
        }
        if (value >= 0) values.add(value);
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
        return out;
    }
}
