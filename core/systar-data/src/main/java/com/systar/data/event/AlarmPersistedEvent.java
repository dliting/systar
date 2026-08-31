package com.systar.data.event;

import org.springframework.context.ApplicationEvent;

public class AlarmPersistedEvent extends ApplicationEvent {

    private final int alarmMessageId;
    private final int eventRankId;
    private final int assetId;

    public AlarmPersistedEvent(Object source, int alarmMessageId, int eventRankId, int assetId) {
        super(source);
        this.alarmMessageId = alarmMessageId;
        this.eventRankId = eventRankId;
        this.assetId = assetId;
    }

    public int getAlarmMessageId() { return alarmMessageId; }
    public int getEventRankId() { return eventRankId; }
    public int getAssetId() { return assetId; }
}
