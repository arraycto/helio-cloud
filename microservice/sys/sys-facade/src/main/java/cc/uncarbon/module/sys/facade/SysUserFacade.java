package cc.uncarbon.module.sys.facade;

import cc.uncarbon.framework.core.page.PageParam;
import cc.uncarbon.framework.core.page.PageResult;
import cc.uncarbon.module.sys.model.request.*;
import cc.uncarbon.module.sys.model.response.SysUserBO;
import cc.uncarbon.module.sys.model.response.SysUserLoginBO;
import cc.uncarbon.module.sys.model.response.VbenAdminUserInfoBO;

import java.util.List;

/**
 * 后台用户Facade接口
 */
public interface SysUserFacade {

    /**
     * 后台管理-分页列表
     */
    PageResult<SysUserBO> adminList(PageParam pageParam, AdminListSysUserDTO dto);

    /**
     * 通用-详情
     */
    SysUserBO getOneById(Long entityId);

    /**
     * 后台管理-新增
     * @return 主键ID
     */
    Long adminInsert(AdminInsertOrUpdateSysUserDTO dto);

    /**
     * 后台管理-编辑
     */
    void adminUpdate(AdminInsertOrUpdateSysUserDTO dto);

    /**
     * 后台管理-删除
     */
    void adminDelete(List<Long> ids);

    /**
     * 后台管理-登录
     */
    SysUserLoginBO adminLogin(SysUserLoginDTO dto);

    /**
     * 后台管理-取当前用户信息
     */
    VbenAdminUserInfoBO adminGetCurrentUserInfo();

    /**
     * 后台管理-重置某用户密码
     */
    void adminResetUserPassword(AdminResetSysUserPasswordDTO dto);

    /**
     * 后台管理-修改当前用户密码
     */
    void adminUpdateCurrentUserPassword(AdminUpdateCurrentSysUserPasswordDTO dto);

    /**
     * 后台管理-绑定用户与角色关联关系
     */
    void adminBindRoles(AdminBindUserRoleRelationDTO dto);

}
