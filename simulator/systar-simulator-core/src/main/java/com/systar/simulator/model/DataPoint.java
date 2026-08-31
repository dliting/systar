package com.systar.simulator.model;

import com.systar.simulator.generator.DataGenerator;

public class DataPoint {
    private String           id;
    private String           name;
    private DataPointAddress address;
    private DataGenerator    generator;
    private volatile Object  currentValue;
    private volatile long    lastUpdateMillis;
    private volatile Object  override;

    public DataPoint() {}

    public String            getId()               { return id; }
    public void              setId(String id)       { this.id = id; }
    public String            getName()              { return name; }
    public void              setName(String name)   { this.name = name; }
    public DataPointAddress  getAddress()           { return address; }
    public void              setAddress(DataPointAddress a) { this.address = a; }
    public DataGenerator     getGenerator()         { return generator; }
    public void              setGenerator(DataGenerator g)  { this.generator = g; }
    public Object            getCurrentValue()      { return currentValue; }
    public void              setCurrentValue(Object v) { this.currentValue = v; }
    public long              getLastUpdateMillis()  { return lastUpdateMillis; }
    public void              setLastUpdateMillis(long t) { this.lastUpdateMillis = t; }
    public Object            getOverride()          { return override; }
    public void              setOverride(Object o)  { this.override = o; }
}
