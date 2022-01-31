package de.geolykt.starloader.deobf;

import java.util.Comparator;

public class AlphabeticFieldReferenceComparator implements Comparator<FieldReference> {

    @Override
    public int compare(FieldReference o1, FieldReference o2) {
        int len1 = o1.getName().length();
        int len2 = o2.getName().length();
        if (len1 == len2) {
            return o1.getName().compareTo(o2.getName());
        } else {
            return len1 - len2;
        }
    }
}
