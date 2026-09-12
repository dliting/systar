package com.systar.server.dto;

import java.util.List;

public record GroupMembersRequest(List<Long> assetIds) {
}
