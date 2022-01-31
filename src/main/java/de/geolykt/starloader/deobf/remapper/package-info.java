/**
 * Simple in-memory remapper engine. Unlike many other remappers it is able to take in already parsed
 * {@link org.objectweb.asm.tree.ClassNode Objectweb ASM Classnodes} as input and output them without having
 * to go through an intermediary store-to-file mode.
 */
package de.geolykt.starloader.deobf.remapper;
