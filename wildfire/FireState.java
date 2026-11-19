package wildfire;

/** Lifecycle state of a cell. Transitions only go UNBURNED -> BURNING -> BURNED_OUT. */
public enum FireState {
    UNBURNED,
    BURNING,
    BURNED_OUT
}
