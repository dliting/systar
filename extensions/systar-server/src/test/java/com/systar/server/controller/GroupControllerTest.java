package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.server.controller.vo.GroupTreeVO;
import com.systar.server.controller.vo.GroupVO;
import com.systar.server.controller.vo.TreeNodeVO;
import com.systar.server.dto.GroupMembersRequest;
import com.systar.server.dto.GroupRequest;
import com.systar.server.dto.GroupTreeRequest;
import com.systar.server.repository.GroupRepository;
import com.systar.server.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class GroupControllerTest {

    private GroupService    groupService;
    private GroupController controller;

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        controller   = new GroupController(groupService);
    }

    @Nested
    @DisplayName("GET /api/monitor/asset-tree")
    class GetAssetTree {

        @Test
        @DisplayName("passes the tree selector through to the service")
        void passesSelector() {
            List<TreeNodeVO> forest = List.of();
            when(groupService.buildAssetTree("kind")).thenReturn(forest);

            Result<List<TreeNodeVO>> result = controller.getAssetTree("kind");

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isSameAs(forest);
            verify(groupService).buildAssetTree("kind");
        }

        @Test
        @DisplayName("tree param carries defaultValue kind (a missing param still resolves)")
        void treeParamDefaultsToKind() throws Exception {
            RequestParam param = GroupController.class
                    .getMethod("getAssetTree", String.class)
                    .getParameterAnnotations()[0][0] instanceof RequestParam rp ? rp : null;

            assertThat(param).isNotNull();
            assertThat(param.defaultValue()).isEqualTo("kind");
        }
    }

    @Nested
    @DisplayName("group-trees endpoints")
    class GroupTrees {

        @Test
        @DisplayName("GET /group-trees maps rows to VOs")
        void listTrees() {
            when(groupService.listTrees()).thenReturn(List.of(
                    new GroupRepository.GroupTreeRow(1L, "region", "按区域", 1)));

            Result<List<GroupTreeVO>> result = controller.listTrees();

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).containsExactly(
                    new GroupTreeVO(1L, "region", "按区域", 1));
        }

        @Test
        @DisplayName("POST /group-trees passes fields through, null sequence becomes 0")
        void createTree() {
            Result<Void> result = controller.createTree(new GroupTreeRequest("region", "按区域", null));

            assertThat(result.getCode()).isEqualTo(0);
            verify(groupService).createTree("region", "按区域", 0);
        }

        @Test
        @DisplayName("PUT /group-trees/{id} with null caption falls back to the name")
        void updateTreeNullCaptionFallsBackToName() {
            Result<Void> result = controller.updateTree(1L, new GroupTreeRequest("region", null, 2));

            assertThat(result.getCode()).isEqualTo(0);
            verify(groupService).updateTree(1L, "region", "region", 2);
        }

        @Test
        @DisplayName("PUT /group-trees/{id} keeps a provided caption")
        void updateTreeKeepsCaption() {
            controller.updateTree(1L, new GroupTreeRequest("region", "按区域", 2));

            verify(groupService).updateTree(1L, "region", "按区域", 2);
        }

        @Test
        @DisplayName("DELETE /group-trees/{id} delegates to the service")
        void deleteTree() {
            Result<Void> result = controller.deleteTree(1L);

            assertThat(result.getCode()).isEqualTo(0);
            verify(groupService).deleteTree(1L);
        }
    }

    @Nested
    @DisplayName("groups endpoints")
    class Groups {

        @Test
        @DisplayName("GET /groups delegates with the requested treeId")
        void listGroups() {
            GroupVO vo = new GroupVO(7L, 1L, "g", "G", 0L, 1, 1, List.of(22L));
            when(groupService.listGroupVOs(1L)).thenReturn(List.of(vo));

            Result<List<GroupVO>> result = controller.listGroups(1L);

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).containsExactly(vo);
        }

        @Test
        @DisplayName("POST /groups with missing treeId falls back to 0 instead of NPE")
        void createGroupWithMissingTreeIdFallsBackToZero() {
            Result<Void> result = controller.createGroup(
                    new GroupRequest(null, "g", "G", null, null));

            assertThat(result.getCode()).isEqualTo(0);
            verify(groupService).createGroup(0L, "g", "G", 0L, 0);
        }

        @Test
        @DisplayName("POST /groups defaults null parent to top-level")
        void createGroupDefaultsParent() {
            controller.createGroup(new GroupRequest(1L, "g", "G", null, 2));

            verify(groupService).createGroup(1L, "g", "G", 0L, 2);
        }

        @Test
        @DisplayName("PUT /groups/{id} with parent and treeId moves then renames")
        void updateGroupMovesThenRenames() {
            controller.updateGroup(7L, new GroupRequest(1L, "g", "G", 5L, 3));

            verify(groupService).moveGroup(1L, 7L, 5L);
            verify(groupService).renameGroup(7L, "g", "G", 3);
        }

        @Test
        @DisplayName("PUT /groups/{id} without parent renames only")
        void updateGroupWithoutParentSkipsMove() {
            controller.updateGroup(7L, new GroupRequest(1L, "g", "G", null, 3));

            verify(groupService, never()).moveGroup(anyLong(), anyLong(), anyLong());
            verify(groupService).renameGroup(7L, "g", "G", 3);
        }

        @Test
        @DisplayName("PUT /groups/{id} with only parent+treeId moves without renaming")
        void updateGroupMoveOnlySkipsRename() {
            Result<Void> result = controller.updateGroup(7L, new GroupRequest(1L, null, null, 5L, null));

            assertThat(result.getCode()).isEqualTo(0);
            verify(groupService).moveGroup(1L, 7L, 5L);
            // Argument-agnostic pin: renameGroup (and anything else) must not be touched.
            verifyNoMoreInteractions(groupService);
        }

        @Test
        @DisplayName("PUT /groups/{id} with nothing to update is rejected")
        void updateGroupNothingToUpdate() {
            assertThatThrownBy(() -> controller.updateGroup(7L, new GroupRequest(1L, null, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Nothing to update");
            verify(groupService, never()).moveGroup(anyLong(), anyLong(), anyLong());
            verify(groupService, never()).renameGroup(anyLong(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("PUT /groups/{id} with null caption falls back to the name and null sequence passes through")
        void updateGroupNullCaptionFallsBackToName() {
            controller.updateGroup(7L, new GroupRequest(1L, "g", null, null, null));

            // An omitted sequence is forwarded untouched: the service keeps the stored value.
            verify(groupService).renameGroup(7L, "g", "g", null);
        }

        @Test
        @DisplayName("DELETE /groups/{id} delegates to the service")
        void deleteGroup() {
            Result<Void> result = controller.deleteGroup(7L);

            assertThat(result.getCode()).isEqualTo(0);
            verify(groupService).deleteGroup(7L);
        }

        @Test
        @DisplayName("PUT /groups/{id}/assets delegates the member list")
        void replaceGroupAssets() {
            controller.replaceGroupAssets(7L, new GroupMembersRequest(List.of(22L, 23L)));

            verify(groupService).replaceGroupAssets(7L, List.of(22L, 23L));
        }

        @Test
        @DisplayName("PUT /groups/{id}/assets with null member list delegates null")
        void replaceGroupAssetsNullList() {
            controller.replaceGroupAssets(7L, new GroupMembersRequest(null));

            verify(groupService).replaceGroupAssets(7L, null);
        }
    }

    @Nested
    @DisplayName("permission codes")
    class PermissionCodes {

        @Test
        @DisplayName("delete endpoints carry the canonical iot:asset:delete permission")
        void deleteEndpointsUseCanonicalPermission() throws Exception {
            for (String method : List.of("deleteTree", "deleteGroup")) {
                RequirePermission permission = GroupController.class
                        .getMethod(method, long.class)
                        .getAnnotation(RequirePermission.class);
                assertThat(permission).as(method).isNotNull();
                assertThat(permission.value()).as(method).isEqualTo("iot:asset:delete");
            }
        }
    }
}
