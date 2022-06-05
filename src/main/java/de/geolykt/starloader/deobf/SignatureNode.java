package de.geolykt.starloader.deobf;

import java.util.Objects;

public class SignatureNode {

    protected String generic;
    public final String type;

    public SignatureNode(String type, String generics) {
        this.generic = generics;
        if (type.endsWith(";")) {
            type = type.substring(0, type.length() - 1);
        }
        this.type = type;
    }

    public void setGenerics(String generics) {
        this.generic = generics;
    }

    public void setGenerics(SignatureNode node) {
        if (node == null) {
            this.generic = null;
        } else {
            this.generic = node.toString();
        }
    }

    @Override
    public String toString() {
        if (generic == null) {
            return null;
        }
        return type + '<' + generic + ">;";
    }

    @Override
    public int hashCode() {
        return Objects.hash(generic, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SignatureNode)) {
            return false;
        }
        SignatureNode n = (SignatureNode) obj;
        return n.type.equals(type) && ((generic == null && n.generic == null) || (generic != null && generic.equals(n.generic)));
    }
}
