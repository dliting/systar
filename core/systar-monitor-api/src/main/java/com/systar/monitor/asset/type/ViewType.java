package com.systar.monitor.asset.type;

/**
 * Determines how a monitor value should be presented in the UI.
 * <p>
 * ViewType is independent of DataType: DataType describes the value format,
 * ViewType describes the presentation widget.
 */
public enum ViewType {
    TEXTFIELD,
    TEXTAREA,
    LIST,
    PASSWORD,
    YESNO,
    PERCENT,
    SLIDER;

    /** Infer a default ViewType from DataType when no explicit ViewType is declared. */
    public static ViewType infer(DataType dataType) {
        if (dataType == null) return TEXTFIELD;
        return switch (dataType) {
            case BOOLEAN  -> YESNO;
            case INT, FLOAT, STRING, TIMESPAN -> TEXTFIELD;
        };
    }
}
