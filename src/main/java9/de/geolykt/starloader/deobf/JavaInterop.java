package de.geolykt.starloader.deobf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

class JavaInterop {

    @NotNull
    public static String codepointToString(int codepoint) {
        return new String(new int[] {codepoint}, 0, 1);
    }

    @SafeVarargs
    public static <T> Set<T> modifableSet(@NotNull T @NotNull... objects) {
        Set<T> set = new HashSet<>();
        for (T o : objects) {
            set.add(o);
        }
        return set;
    }

    @NotNull
    public static ClassLoader newURLClassloader(@NotNull String name, URL @NotNull[] urls, ClassLoader parent) {
        return new URLClassLoader(name, urls, parent);
    }

    public static byte @NotNull[] readAllBytes(@NotNull InputStream in) throws IOException {
    	return in.readAllBytes();
    }

    public static void transferTo(@NotNull InputStream source, @NotNull OutputStream sink) throws IOException {
    	source.transferTo(sink);
    }

    @SafeVarargs
    public static <T> Set<T> unmodifableSet(@NotNull T @NotNull... objects) {
    	return Set.of(objects);
    }
}
