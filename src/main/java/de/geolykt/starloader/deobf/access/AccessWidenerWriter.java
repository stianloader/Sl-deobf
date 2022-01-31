package de.geolykt.starloader.deobf.access;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class AccessWidenerWriter {

    public static void writeAccessWidener(AccessTransformInfo atInfo, OutputStream out, boolean writeHeader) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out))) {
            if (writeHeader) {
                bw.write("accessWidener   v2     intermediary");
                bw.newLine();
            }
            for (AccessFlagModifier modifier : atInfo.modifiers) {
                bw.write(modifier.toAccessWidenerString());
                bw.newLine();
            }
        }
    }
}
