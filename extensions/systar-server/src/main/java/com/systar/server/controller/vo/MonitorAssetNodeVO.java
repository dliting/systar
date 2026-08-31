package com.systar.server.controller.vo;

/**
 * VO for tree node of Monitor assets (Probe, Control).
 * Extends {@link AssetNodeVO} with Monitor-specific dataType and viewType.
 */
public class MonitorAssetNodeVO extends AssetNodeVO {

    /** Monitor primary data type from type definition. */
    private String dataType;
    /** Monitor value presentation type from type definition. */
    private String viewType;

    public String getDataType()                 { return dataType; }
    public void   setDataType(String dataType)  { this.dataType = dataType; }
    public String getViewType()                 { return viewType; }
    public void   setViewType(String viewType)  { this.viewType = viewType; }
}