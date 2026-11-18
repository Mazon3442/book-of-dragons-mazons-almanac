package local.bodaddon.editor;

import net.magister.liberanimalium.fauna.identity.Trait;

/** The six personality traits the Fauna Engine tracks, in a fixed order shared by client and server. */
public final class Traits {
    public static final Trait[] ALL = Trait.values();
    public static final int COUNT = ALL.length;

    public static String langKey(int i) { return "bodaddon.editor.trait." + ALL[i].key(); }

    /** Radar chart label: the trait name, with a shorter one for nervousness so it fits. */
    public static String chartKey(int i) { return ALL[i] == Trait.NERVOUSNESS ? "bodaddon.editor.trait.nerves" : langKey(i); }

    private Traits() {}
}
