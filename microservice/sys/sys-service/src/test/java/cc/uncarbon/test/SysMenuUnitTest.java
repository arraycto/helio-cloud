package cc.uncarbon.test;


import cc.uncarbon.framework.core.constant.HelioConstant;
import cc.uncarbon.framework.core.context.TenantContext;
import cc.uncarbon.framework.core.context.UserContext;
import cc.uncarbon.framework.core.context.UserContextHolder;
import cc.uncarbon.module.sys.SysServiceApplication;
import cc.uncarbon.module.sys.entity.SysMenuEntity;
import cc.uncarbon.module.sys.enums.GenericStatusEnum;
import cc.uncarbon.module.sys.enums.SysMenuTypeEnum;
import cc.uncarbon.module.sys.enums.UserTypeEnum;
import cc.uncarbon.module.sys.model.request.AdminInsertOrUpdateSysMenuDTO;
import cc.uncarbon.module.sys.service.SysMenuService;
import cc.uncarbon.module.sys.service.SysRoleMenuRelationService;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;


@SpringBootTest(classes = SysServiceApplication.class)
public class SysMenuUnitTest {

    @Resource
    private SysMenuService sysMenuService;

    @Resource
    private SysRoleMenuRelationService sysRoleMenuRelationService;

    @BeforeEach
    public void setCurrentUser() {
        UserContext userContext = new UserContext();
        userContext
                .setUserId(1L)
                // 用户类型, 根据单元测试需要修改
                .setUserType(UserTypeEnum.ADMIN_USER)
                .setRelationalTenant(
                        TenantContext.builder()
                                .tenantId(HelioConstant.CRUD.PRIVILEGED_TENANT_ID)
                                .build()
                )
        ;
        UserContextHolder.setUserContext(userContext);
    }

    @Test
    public void insertInitData() {
        Long grandParentId = 1L;

        String[] sideMenuTitles = {"部门管理", "角色管理", "用户管理", "菜单管理", "参数管理", "租户管理", "数据字典", "系统日志"};
        String[] sideMenuAliases = {"SysDept", "SysRole", "SysUser", "SysMenu", "SysParam", "SysTenant", "SysDataDict", "SysLog"};
        String[] buttonTitles = {"查询", "新增", "删除", "编辑"};
        String[] buttonPermissions = {HelioConstant.Permission.RETRIEVE, HelioConstant.Permission.CREATE, HelioConstant.Permission.DELETE, HelioConstant.Permission.UPDATE};

        for (int menuIndex = 0; menuIndex < sideMenuTitles.length; menuIndex++) {
            Long parentId = sysMenuService.adminInsert(
                    AdminInsertOrUpdateSysMenuDTO.builder()
                            .title(sideMenuTitles[menuIndex])
                            .parentId(grandParentId)
                            .type(SysMenuTypeEnum.MENU)
                            .permission(sideMenuAliases[menuIndex])
                            .component(String.format("/sys/%s/index", sideMenuAliases[menuIndex]))
                            .sort(menuIndex + 1)
                            .status(GenericStatusEnum.ENABLED)
                            .build()
            );


            for (int permissionIndex = 0; permissionIndex < buttonTitles.length; permissionIndex++) {
                Long buttonId = sysMenuService.adminInsert(
                        AdminInsertOrUpdateSysMenuDTO.builder()
                                .title(buttonTitles[permissionIndex])
                                .parentId(parentId)
                                .type(SysMenuTypeEnum.BUTTON)
                                .permission(sideMenuAliases[menuIndex] + ":" + buttonPermissions[permissionIndex])
                                .sort(permissionIndex + 1)
                                .status(GenericStatusEnum.ENABLED)
                                .build()
                );
            }
        }
    }

    /**
     * 向表中加入CRUD按钮
     */
    @Test
    public void insertCRUDButton() {
        String[] prefixes = {"SysDept", "SysRole", "SysUser", "SysMenu", "SysParam", "SysTenant", "SysLog"};
        String[] suffixes = {HelioConstant.Permission.RETRIEVE, HelioConstant.Permission.CREATE, HelioConstant.Permission.UPDATE, HelioConstant.Permission.DELETE};
        String[] titles = {"查询", "新增", "编辑", "删除"};

        CollUtil.newArrayList(prefixes).forEach(
                prefix -> {
                    SysMenuEntity parentEntity = sysMenuService.getOne(
                            new QueryWrapper<SysMenuEntity>()
                                    .lambda()
                                    .eq(SysMenuEntity::getPermission, prefix)
                                    .last(" LIMIT 1")
                    );

                    if (parentEntity != null) {
                        for (int i = 0; i < titles.length; i++) {
                            sysMenuService.adminInsert(
                                    AdminInsertOrUpdateSysMenuDTO.builder()
                                            .title(titles[i])
                                            .parentId(parentEntity.getId())
                                            .type(SysMenuTypeEnum.BUTTON)
                                            .permission(prefix + ":" + suffixes[i])
                                            .sort(i + 1)
                                            .status(GenericStatusEnum.ENABLED)
                                            .build()
                            );
                        }
                    }
                }
        );
    }
}
