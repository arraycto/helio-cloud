package cc.uncarbon.module.sys.service;

import cc.uncarbon.framework.core.constant.HelioConstant;
import cc.uncarbon.framework.core.context.UserContextHolder;
import cc.uncarbon.framework.core.exception.BusinessException;
import cc.uncarbon.framework.crud.service.impl.HelioBaseServiceImpl;
import cc.uncarbon.module.sys.annotation.SysLog;
import cc.uncarbon.module.sys.constant.SysConstant;
import cc.uncarbon.module.sys.entity.SysMenuEntity;
import cc.uncarbon.module.sys.enums.GenericStatusEnum;
import cc.uncarbon.module.sys.enums.SysMenuTypeEnum;
import cc.uncarbon.module.sys.mapper.SysMenuMapper;
import cc.uncarbon.module.sys.model.request.AdminInsertOrUpdateSysMenuDTO;
import cc.uncarbon.module.sys.model.request.AdminListSysMenuDTO;
import cc.uncarbon.module.sys.model.response.SysMenuBO;
import cc.uncarbon.module.sys.model.response.VbenAdminMenuMetaBO;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 后台菜单
 * @author Uncarbon
 */
@Slf4j
@Service
public class SysMenuService extends HelioBaseServiceImpl<SysMenuMapper, SysMenuEntity> {

    @Resource
    private SysRoleMenuRelationService sysRoleMenuRelationService;

    @Resource
    private SysUserRoleRelationService sysUserRoleRelationService;

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(0L, 0L);


    /**
     * 后台管理-列表
     */
    public List<SysMenuBO> adminList(AdminListSysMenuDTO dto) {
        List<SysMenuEntity> entityList = this.list(
                new QueryWrapper<SysMenuEntity>()
                        .lambda()
                        // 标题
                        .like(StrUtil.isNotBlank(dto.getTitle()), SysMenuEntity::getTitle, StrUtil.cleanBlank(dto.getTitle()))
                        // 上级ID
                        .eq(SysMenuEntity::getParentId, ObjectUtil.isNull(dto.getParentId()) ? SysConstant.ROOT_PARENT_ID : dto.getParentId())
                        // 菜单类型
                        .in(CollUtil.isNotEmpty(dto.getMenuTypes()), SysMenuEntity::getType, dto.getMenuTypes())
                        // 排序
                        .orderByAsc(SysMenuEntity::getSort)
        );

        return this.entityList2BOs(entityList, true);
    }

    /**
     * 通用-详情
     */
    public SysMenuBO getOneById(Long entityId) {
        SysMenuEntity entity = this.getById(entityId);
        if (entity == null) {
            throw new BusinessException(400, HelioConstant.Message.INVALID_ID);
        }

        return this.entity2BO(entity, false, null);
    }

    /**
     * 后台管理-新增
     * @return 主键ID
     */
    @SysLog(value = "新增后台菜单")
    @Transactional(rollbackFor = Exception.class)
    public Long adminInsert(AdminInsertOrUpdateSysMenuDTO dto) {
        this.checkExist(dto);

        if (ObjectUtil.isNull(dto.getParentId())) {
            dto.setParentId(0L);
        }

        dto.setId(null);

        SysMenuEntity entity = new SysMenuEntity();
        BeanUtil.copyProperties(dto, entity);

        this.save(entity);

        return entity.getId();
    }

    /**
     * 后台管理-编辑
     */
    @SysLog(value = "编辑后台菜单")
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdate(AdminInsertOrUpdateSysMenuDTO dto) {
        this.checkExist(dto);

        if (ObjectUtil.isNull(dto.getParentId())) {
            dto.setParentId(0L);
        }

        SysMenuEntity entity = new SysMenuEntity();
        BeanUtil.copyProperties(dto, entity);

        this.updateById(entity);
    }

    /**
     * 后台管理-删除
     */
    @SysLog(value = "删除后台菜单")
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(List<Long> ids) {
        this.removeByIds(ids);
    }

    /**
     * 后台管理-根据角色Ids取权限串List
     */
    public List<String> adminListPermissionByRoleIds(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return CollUtil.newArrayList();
        }

        // 超级管理员直接允许所有权限
        if (roleIds.contains(SysConstant.SUPER_ADMIN_ROLE_ID)) {
            return this.list().stream().map(SysMenuEntity::getPermission).collect(Collectors.toList());
        }

        List<Long> menuIds = sysRoleMenuRelationService.listMenuIdByRoleIds(roleIds);

        if (CollUtil.isEmpty(menuIds)) {
            return CollUtil.newArrayList();
        }

        return this.list(
                new QueryWrapper<SysMenuEntity>()
                        .select(" DISTINCT permission ")
                        .lambda()
                        .in(SysMenuEntity::getId, menuIds)
                        .ne(SysMenuEntity::getPermission, "")
        ).stream().map(SysMenuEntity::getPermission).collect(Collectors.toList());
    }

    /**
     * 后台管理-取当前账号可见侧边菜单
     */
    public List<SysMenuBO> adminListSideMenu() {
        List<Long> visibleMenuIds = this.listCurrentUserVisibleMenuId();

        // 3. 取出无上级节点菜单
        List<SysMenuTypeEnum> requiredMenuTypes = CollUtil.newArrayList(SysMenuTypeEnum.DIR, SysMenuTypeEnum.MENU, SysMenuTypeEnum.EXTERNAL_LINK);
        List<SysMenuBO> allMenus = this.listMenuByParentId(visibleMenuIds, requiredMenuTypes, SysConstant.ROOT_PARENT_ID);
        log.debug("取出无上级节点菜单:{}", allMenus);

        // 4.递归查询子节点, 孙节点, 曾孙节点...
        this.recursiveFindChildren(visibleMenuIds, requiredMenuTypes, allMenus);
        log.debug("递归查询子节点, 孙节点, 曾孙节点...:{}", allMenus);

        return allMenus;
    }

    /**
     * 后台管理-取当前账号所有可见菜单(包括按钮类型)
     */
    public List<SysMenuBO> adminListVisibleMenu() {
        List<Long> visibleMenuIds = this.listCurrentUserVisibleMenuId();

        // 3. 取出无上级节点菜单
        List<SysMenuTypeEnum> requiredMenuTypes = CollUtil.newArrayList(SysMenuTypeEnum.DIR, SysMenuTypeEnum.MENU, SysMenuTypeEnum.EXTERNAL_LINK, SysMenuTypeEnum.BUTTON);
        List<SysMenuBO> allMenus = this.listMenuByParentId(visibleMenuIds, requiredMenuTypes, SysConstant.ROOT_PARENT_ID);
        log.debug("取出无上级节点菜单:{}", allMenus);

        // 4.递归查询子节点, 孙节点, 曾孙节点...
        this.recursiveFindChildren(visibleMenuIds, requiredMenuTypes, allMenus);
        log.debug("递归查询子节点, 孙节点, 曾孙节点...:{}", allMenus);

        return allMenus;
    }

    /**
     * 内部-过滤被禁用的菜单ID
     *
     * @param visibleMenuIds 待过滤的可见的菜单ID列表
     * @return 过滤后的菜单ID列表
     */
    public List<Long> filterDisabledIds(List<Long> visibleMenuIds) {
        if (CollUtil.isEmpty(visibleMenuIds)) {
            return visibleMenuIds;
        }

        /*
        1. 找出目前被禁用的菜单ID
         */
        List<Long> disabledMenuIds = this.list(
                new QueryWrapper<SysMenuEntity>()
                        .select(" id ")
                        .lambda()
                        .eq(SysMenuEntity::getStatus, GenericStatusEnum.DISABLED)
        ).stream().map(SysMenuEntity::getId).collect(Collectors.toList());


        /*
        2. 提取出符合以下条件的菜单ID:
            启用状态 && 父菜单未被禁用
         */
        return this.list(
                new QueryWrapper<SysMenuEntity>()
                        .select(" id ")
                        .lambda()
                        .and(
                                wrapper -> wrapper
                                        .eq(SysMenuEntity::getStatus, GenericStatusEnum.ENABLED)
                                        .notIn(CollUtil.isNotEmpty(disabledMenuIds), SysMenuEntity::getParentId, disabledMenuIds)
                        )
                        .in(SysMenuEntity::getId, visibleMenuIds)
        ).stream().map(SysMenuEntity::getId).collect(Collectors.toList());
    }
    

    /*
    私有方法
    ------------------------------------------------------------------------------------------------
     */

    private SysMenuBO entity2BO(SysMenuEntity entity, Boolean traverseChildren, List<SysMenuTypeEnum> menuTypes) {
        if (entity == null) {
            return null;
        }

        SysMenuBO bo = new SysMenuBO();
        BeanUtil.copyProperties(entity, bo);

        // 可以在此处为BO填充字段
        if (SysConstant.ROOT_PARENT_ID.equals(bo.getParentId())) {
            bo.setParentId(null);
        }

        String snowflakeIdStr = SNOWFLAKE.nextIdStr();
        bo
                .setName(snowflakeIdStr)
                .setMeta(
                        VbenAdminMenuMetaBO.builder()
                                .title(bo.getTitle())
                                .affix(false)
                                .icon(bo.getIcon())
                                .build()
                );

        switch (bo.getType()) {
            case DIR, BUTTON -> bo
                    .setComponent(SysConstant.VBEN_ADMIN_BLANK_VIEW)
                    .setExternalLink(null)
                    .setPath("/" + snowflakeIdStr)
            ;
            case MENU -> {
                bo
                        .setExternalLink(null)
                        .setPath(bo.getComponent())
                ;
                // 防止用户忘记加了, 主动补充/
                if (StrUtil.isNotBlank(bo.getPath()) && !bo.getPath().startsWith("/")) {
                    bo.setPath("/" + bo.getPath());
                }
            }
            case EXTERNAL_LINK -> bo
                    .setComponent(bo.getExternalLink())
                    .setPath(bo.getExternalLink())
            ;
        }

        // 遍历子级菜单
        if (traverseChildren) {
            List<SysMenuBO> children = this.adminList(
                    AdminListSysMenuDTO.builder()
                            .parentId(bo.getId())
                            .menuTypes(menuTypes)
                            .build()
            );
            if (CollUtil.isEmpty(children)) {
                children = null;
            }

            bo.setChildren(children);
        }

        return bo;
    }

    private List<SysMenuBO> entityList2BOs(List<SysMenuEntity> entityList, Boolean traverseChildren) {
        // 深拷贝
        List<SysMenuBO> ret = new ArrayList<>(entityList.size());
        entityList.forEach(
                entity -> ret.add(this.entity2BO(entity, traverseChildren, null))
        );

        return ret;
    }

    /**
     * 取当前账号可见菜单Ids
     * @return 菜单Ids
     */
    private List<Long> listCurrentUserVisibleMenuId() {
        // 1. 取当前账号拥有角色Ids
        List<Long> roleIds = sysUserRoleRelationService.listRoleIdByUserId(UserContextHolder.getUserId());
        log.debug("取当前账号拥有角色Ids:{}", roleIds);
        if (CollUtil.isEmpty(roleIds)) {
            throw new BusinessException(400, "当前账号没有可用角色");
        }

        // 2-1. 超级管理员直接允许所有菜单，即使是禁用状态
        if (roleIds.contains(SysConstant.SUPER_ADMIN_ROLE_ID)) {
            return this.list().stream().map(SysMenuEntity::getId).collect(Collectors.toList());
        }

        // 2-2. 根据角色Ids取菜单Ids
        List<Long> menuIds = sysRoleMenuRelationService.listMenuIdByRoleIds(roleIds);
        log.debug("根据角色Ids取菜单Ids:{}", menuIds);
        if (CollUtil.isEmpty(menuIds)) {
            throw new BusinessException(400, "当前角色没有可用菜单");
        }

        // 3. 临时补充上级菜单ID
        // 这个Ant-Design的Tree组件有个吊诡的地方, 只选一部分子级的话, 父级菜单不算勾选
        // 只能在代码里补上了
        List<Long> missingParentIds = this.listMissingParentId(menuIds);
        menuIds.addAll(missingParentIds);

        return menuIds;
    }

    private List<SysMenuBO> listMenuByParentId(List<Long> visibleMenuIds, List<SysMenuTypeEnum> requiredMenuTypes, Long parentId) throws IllegalArgumentException {
        if (CollUtil.isEmpty(visibleMenuIds)) {
            throw new IllegalArgumentException("visibleMenuIds不能为空");
        }

        if (CollUtil.isEmpty(requiredMenuTypes)) {
            throw new IllegalArgumentException("requiredMenuTypes不能为空");
        }

        List<SysMenuEntity> entityList = this.list(
                new QueryWrapper<SysMenuEntity>()
                        .lambda()
                        .in(SysMenuEntity::getId, visibleMenuIds)
                        .in(SysMenuEntity::getType, requiredMenuTypes)
                        .eq(SysMenuEntity::getParentId, parentId)
                        .orderByAsc(SysMenuEntity::getSort)
        );

        if (CollUtil.isEmpty(entityList)) {
            return CollUtil.newArrayList();
        }

        List<SysMenuBO> ret = new ArrayList<>(entityList.size());
        entityList.forEach(
                each -> ret.add(this.entity2BO(each, false, requiredMenuTypes))
        );

        return ret;
    }

    /**
     * 递归查询子节点
     */
    private void recursiveFindChildren(List<Long> visibleMenuIds, List<SysMenuTypeEnum> requiredMenuTypes, List<SysMenuBO> menuBOs) {
        menuBOs.forEach(
                menu -> {
                    // 查询子节点
                    List<SysMenuBO> itsChildren = this.listMenuByParentId(visibleMenuIds, requiredMenuTypes, menu.getId());
                    if (CollUtil.isNotEmpty(itsChildren)) {
                        menu.setChildren(itsChildren);
                        // 递归查询孙节点
                        this.recursiveFindChildren(visibleMenuIds, requiredMenuTypes, menu.getChildren());
                    }
                }
        );
    }

    /**
     * 检查是否已存在同名数据
     * @param dto DTO
     */
    private void checkExist(AdminInsertOrUpdateSysMenuDTO dto) {
        if (StrUtil.isNotBlank(dto.getPermission())) {
            dto.setPermission(StrUtil.cleanBlank(dto.getPermission()));

            SysMenuEntity existEntity = this.getOne(
                    new QueryWrapper<SysMenuEntity>()
                            .lambda()
                            .eq(SysMenuEntity::getPermission, dto.getPermission())
                            .last(" LIMIT 1")
            );

            if (existEntity != null && !existEntity.getId().equals(dto.getId())) {
                throw new BusinessException(400, "已存在相同权限标识，请重新输入");
            }
        }
    }

    /**
     * 补充遗漏的上级菜单ID
     */
    private List<Long> listMissingParentId(List<Long> menuIds) {
        // 返回值
        List<Long> ret = new ArrayList<>(32);
        // 单次循环返回值
        List<Long> loopRet = menuIds;

        for (int i = 0; i < 5; i++) {
            // 最多上溯5层
            loopRet = this.list(
                    new QueryWrapper<SysMenuEntity>()
                            .select(" DISTINCT parent_id ")
                            .lambda()
                            .ne(SysMenuEntity::getParentId, SysConstant.ROOT_PARENT_ID)
                            .in(SysMenuEntity::getId, loopRet)
                            .notIn(SysMenuEntity::getParentId, loopRet)
            ).stream().map(SysMenuEntity::getParentId).collect(Collectors.toList());

            if (loopRet.size() == 0) {
                break;
            } else {
                ret.addAll(loopRet);
            }
        }

        return ret;
    }
}