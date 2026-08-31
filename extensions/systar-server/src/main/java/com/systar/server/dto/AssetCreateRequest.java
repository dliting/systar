package com.systar.server.dto;

import java.util.Map;

public record AssetCreateRequest(
        String kind,
        int parentId,
        String name,
        String caption,
        String typeName,
        Map<String, Object> properties,
        Map<String, String> attributes
) {}
