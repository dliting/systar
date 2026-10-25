package com.systar.data.event;

import org.springframework.context.ApplicationEvent;

public class AlarmPersistedEvent extends ApplicationEvent {

    private final int alarmMessageId;
    private final int eventRankId;
    private final int assetId;
    /** Operator-facing alarm title; the raw asset id alone is meaningless in the UI. */
    private final String caption;

    public AlarmPersistedEvent(Object source, int alarmMessageId, int eventRankId, int assetId, String caption) {
        super(source);
        this.alarmMessageId = alarmMessageId;
        this.eventRankId = eventRankId;
        this.assetId = assetId;
        this.caption = caption;
    }

    public int getAlarmMessageId() { return alarmMessageId; }
    public int getEventRankId() { return eventRankId; }
    public int getAssetId() { return assetId; }
    public String getCaption() { return caption; }
}
