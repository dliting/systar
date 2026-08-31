package com.systar.server.controller.vo;

import java.util.Map;

/**
 * VO for general asset information (Space, Device, MonitorService).
 * Monitor-specific fields are in {@link MonitorAssetVO}.
 */
public class AssetVO {

    private int     id;
    private String  name;
    private String  caption;
    private String  kind;       // SPACE, DEVICE, SERVICE, PROBE, CONTROL
    private String  state;      // NORMAL, WARNING, ERROR
    private String  stateCaption; // Human-readable Chinese state label
    private int     parentId;
    private boolean enabled;
    private String  path;       // Full path in asset tree
    private String  typeName;   // Asset type internal name (e.g. "SimulateFloat")
    private String  typeCaption; // Asset type display caption (e.g. "模拟浮点数监测")
    private Map<String, String> attributes; // Extended KV attributes

    public int     getId()                      { return id; }
    public void    setId(int id)                 { this.id = id; }
    public String  getName()                     { return name; }
    public void    setName(String name)          { this.name = name; }
    public String  getCaption()                  { return caption; }
    public void    setCaption(String caption)    { this.caption = caption; }
    public String  getKind()                     { return kind; }
    public void    setKind(String kind)          { this.kind = kind; }
    public String  getState()                    { return state; }
    public void    setState(String state)        { this.state = state; }
    public String  getStateCaption()             { return stateCaption; }
    public void    setStateCaption(String stateCaption) { this.stateCaption = stateCaption; }
    public int     getParentId()                 { return parentId; }
    public void    setParentId(int parentId)     { this.parentId = parentId; }
    public boolean isEnabled()                   { return enabled; }
    public void    setEnabled(boolean enabled)   { this.enabled = enabled; }
    public String  getPath()                     { return path; }
    public void    setPath(String path)          { this.path = path; }
    public String  getTypeName()                 { return typeName; }
    public void    setTypeName(String typeName)  { this.typeName = typeName; }
    public String  getTypeCaption()              { return typeCaption; }
    public void    setTypeCaption(String typeCaption) { this.typeCaption = typeCaption; }
    public Map<String, String> getAttributes()   { return attributes; }
    public void    setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
}