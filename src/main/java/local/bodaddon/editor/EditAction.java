package local.bodaddon.editor;

public enum EditAction {
    TAME,            // instant tame to the acting player
    SET_BOND,        // value = 0..1000 (0 wild, 900+ bonded, 1000 devoted)
    SET_HEALTH,      // value = 1..max health (current health)
    SET_MAX_HEALTH, SET_VARIANT,  // index = step in the species' allowed CON gene combinations
    SET_TRAIT,       // index = trait, value = 0..1
    CLEAR_INJURY,    // heal wounds and incapacitation, cancel injury debuffs
    CLEAR_BREEDING,  // reset breeding / growth timer
    BEFRIEND_NEARBY  // nearby dragons stop fighting and like each other
}
