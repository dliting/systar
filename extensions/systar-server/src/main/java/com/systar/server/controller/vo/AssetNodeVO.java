package com.systar.server.controller.vo;

import java.util.List;

/**
 * VO for tree node of general assets (Space, Device, MonitorService).
 * Monitor-specific fields are in {@link MonitorAssetNodeVO}.
 */
public class AssetNodeVO {

    private int     id;
    private String  name;
    private String  caption;
    private String  kind;
    private String  state;
    private String  stateCaption;
    private String  typeName;
    private String  typeCaption;
    private boolean enabled;
    private List<AssetNodeVO> children;

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
    public String  getTypeName()                 { return typeName; }
    public void    setTypeName(String typeName)  { this.typeName = typeName; }
    public String  getTypeCaption()              { return typeCaption; }
    public void    setTypeCaption(String typeCaption) { this.typeCaption = typeCaption; }
    public boolean isEnabled()                   { return enabled; }
    public void    setEnabled(boolean enabled)   { this.enabled = enabled; }
    public List<AssetNodeVO> getChildren()       { return children; }
    public void    setChildren(List<AssetNodeVO> children) { this.children = children; }
}
