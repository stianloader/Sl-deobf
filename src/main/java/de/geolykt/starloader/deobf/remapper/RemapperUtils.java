package de.geolykt.starloader.deobf.remapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

public final class RemapperUtils {

    private static final boolean isBlank(@NotNull String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (!Character.isWhitespace(string.codePointAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Static utility class.
     */
    private RemapperUtils() {
    }

    public static void readTinyV1File(File tinyMap, Remapper remapper) throws IOException {
        int lineNr = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(tinyMap))) {
            // the first line must specify the version of tiny and the namespace.
            // we are going to ignore the namespace as they just produce too much headache
            String header = br.readLine();
            lineNr++;
            if (header == null || isBlank(header)) {
                br.close();
                throw new IllegalStateException("No tiny header present (empty file?).");
            }
            String[] headerTokens = header.split("\\s+");
            if (headerTokens.length != 3) {
                br.close();
                throw new IllegalStateException("The tiny header had " + headerTokens.length + " tokens, however it is expected to be exactly 3.");
            }
            if (!headerTokens[0].equals("v1")) {
                br.close();
                throw new IllegalStateException("This method can only read tiny v1 maps.");
            }
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                lineNr++;
                if (isBlank(line) || line.charAt(0) == '#') { // fast short-circuiting
                    continue;
                }
                line = line.split("#", 2)[0];
                if (line == null || isBlank(line)) {
                    continue;
                }
                String[] colums = line.split("\\s+");
                String type = colums[0].toUpperCase(Locale.ROOT);
                if (type.equals("CLASS")) {
                    // Format: CLASS originalName newName
                    if (colums.length != 3) {
                        throw new IllegalStateException("Line " + lineNr + " is of type CLASS, but only " + colums.length + " colums are present, even though it expects 3.");
                    }
                    remapper.remapClassName(colums[1], colums[2]);
                } else if (type.equals("METHOD")) {
                    // Format: METHOD owner descriptor originalName newName
                    if (colums.length != 5) {
                        throw new IllegalStateException("Line " + lineNr + " is of type CLASS, but only " + colums.length + " colums are present, even though it expects 5.");
                    }
                    try {
                        remapper.remapMethod(colums[1], colums[2], colums[3], colums[4]);
                    } catch (ConflicitingMappingException e) {
                        e.printStackTrace();
                    }
                } else if (type.equals("FIELD")) {
                    // Format: FIELD owner descriptor originalName newName
                    if (colums.length != 5) {
                        throw new IllegalStateException("Line " + lineNr + " is of type CLASS, but only " + colums.length + " colums are present, even though it expects 5.");
                    }
                    remapper.remapField(colums[1], colums[2], colums[3], colums[4]);
                }
            }
        }
    }

    public static void readReversedTinyV1File(File tinyMap, Remapper remapper) throws IOException {
        int lineNr = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(tinyMap))) {
            // the first line must specify the version of tiny and the namespace.
            // we are going to ignore the namespace as they just produce too much headache
            String header = br.readLine();
            lineNr++;
            if (header == null || isBlank(header)) {
                br.close();
                throw new IllegalStateException("No tiny header present (empty file?).");
            }
            String[] headerTokens = header.split("\\s+");
            if (headerTokens.length != 3) {
                br.close();
                throw new IllegalStateException("The tiny header had " + headerTokens.length + " tokens, however it is expected to be exactly 3.");
            }
            if (!headerTokens[0].equals("v1")) {
                br.close();
                throw new IllegalStateException("This method can only read tiny v1 maps.");
            }
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                lineNr++;
                if (line.charAt(0) == '#') { // fast short-circuiting
                    continue;
                }
                line = line.split("#", 2)[0];
                if (line == null || isBlank(line)) {
                    continue;
                }
                String[] colums = line.split("\\s+");
                String type = colums[0].toUpperCase(Locale.ROOT);
                if (type.equals("CLASS")) {
                    // Format: CLASS originalName newName
                    if (colums.length != 3) {
                        throw new IllegalStateException("Line " + lineNr + " is of type CLASS, but only " + colums.length + " colums are present, even though it expects 3.");
                    }
                    remapper.remapClassName(colums[2], colums[1]);
                } else if (type.equals("METHOD")) {
                    // Format: METHOD owner originalName descriptor newName
                    if (colums.length != 5) {
                        throw new IllegalStateException("Line " + lineNr + " is of type CLASS, but only " + colums.length + " colums are present, even though it expects 5.");
                    }
                    try {
                        remapper.remapMethod(colums[1], colums[3], colums[4], colums[2]);
                    } catch (ConflicitingMappingException e) {
                        e.printStackTrace();
                        System.err.println("This is NOT a fatal error, but it is worth looking into.");
                    }
                } else if (type.equals("FIELD")) {
                    // Format: FIELD owner descriptor originalName newName
                    if (colums.length != 5) {
                        throw new IllegalStateException("Line " + lineNr + " is of type CLASS, but only " + colums.length + " colums are present, even though it expects 5.");
                    }
                    remapper.remapField(colums[1], colums[2], colums[4], colums[3]);
                }
            }
        }
    }
}
