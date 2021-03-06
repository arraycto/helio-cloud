package cc.uncarbon.module.sys.service;

import cc.uncarbon.framework.core.constant.HelioConstant;
import cc.uncarbon.framework.core.exception.BusinessException;
import cc.uncarbon.framework.core.page.PageParam;
import cc.uncarbon.framework.core.page.PageResult;
import cc.uncarbon.framework.crud.service.impl.HelioBaseServiceImpl;
import cc.uncarbon.module.sys.annotation.SysLog;
import cc.uncarbon.module.sys.entity.SysRoleEntity;
import cc.uncarbon.module.sys.mapper.SysRoleMapper;
import cc.uncarbon.module.sys.model.request.AdminInsertOrUpdateSysRoleDTO;
import cc.uncarbon.module.sys.model.request.AdminListSysRoleDTO;
import cc.uncarbon.module.sys.model.response.SysRoleBO;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;


/**
 * 后台角色
 * @author Uncarbon
 */
@Slf4j
@Service
public class SysRoleService extends HelioBaseServiceImpl<SysRoleMapper, SysRoleEntity> {

    @Resource
    private SysUserRoleRelationService sysUserRoleRelationService;

    @Resource
    private SysRoleMenuRelationService sysRoleMenuRelationService;


    /**
     * 后台管理-分页列表
     */
    public PageResult<SysRoleBO> adminList(PageParam pageParam, AdminListSysRoleDTO dto) {
        Page<SysRoleEntity> entityPage = this.page(
                new Page<>(pageParam.getPageNum(), pageParam.getPageSize()),
                new QueryWrapper<SysRoleEntity>()
                        .lambda()
                        // 名称
                        .like(StrUtil.isNotBlank(dto.getTitle()), SysRoleEntity::getTitle, StrUtil.cleanBlank(dto.getTitle()))
                        // 值
                        .like(StrUtil.isNotBlank(dto.getValue()), SysRoleEntity::getValue, StrUtil.cleanBlank(dto.getValue()))
                        // 时间区间
                        .between(ObjectUtil.isNotNull(dto.getBeginAt()) && ObjectUtil.isNotNull(dto.getEndAt()), SysRoleEntity::getCreateAt, dto.getBeginAt(), dto.getEndAt())
                        // 排序
                        .orderByDesc(SysRoleEntity::getCreateAt)
        );

        return this.entityPage2BOPage(entityPage);
    }

    /**
     * 通用-详情
     */
    public SysRoleBO getOneById(Long entityId) {
        SysRoleEntity entity = this.getById(entityId);
        if (entity == null) {
            throw new BusinessException(400, HelioConstant.Message.INVALID_ID);
        }

        return this.entity2BO(entity);
    }

    /**
     * 后台管理-新增
     * @return 主键ID
     */
    @SysLog(value = "新增后台角色")
    @Transactional(rollbackFor = Exception.class)
    public Long adminInsert(AdminInsertOrUpdateSysRoleDTO dto) {
        this.checkExist(dto);

        dto.setId(null);
        SysRoleEntity entity = new SysRoleEntity();
        BeanUtil.copyProperties(dto, entity);

        this.save(entity);

        sysRoleMenuRelationService.cleanAndBind(entity.getId(), dto.getMenuIds());

        return entity.getId();
    }

    /**
     * 后台管理-编辑
     */
    @SysLog(value = "编辑后台角色")
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdate(AdminInsertOrUpdateSysRoleDTO dto) {
        this.checkExist(dto);

        SysRoleEntity entity = new SysRoleEntity();
        BeanUtil.copyProperties(dto, entity);

        sysRoleMenuRelationService.cleanAndBind(dto.getId(), dto.getMenuIds());

        this.updateById(entity);
    }

    /**
     * 后台管理-删除
     */
    @SysLog(value = "删除后台角色")
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(List<Long> ids) {
        this.removeByIds(ids);
    }

    /**
     * 取拥有角色列表
     * @param userId 用户ID
     * @return 失败返回空列表
     */
    public List<SysRoleBO> listRoleByUserId(Long userId) {
        List<Long> roleIds = sysUserRoleRelationService.listRoleIdByUserId(userId);

        if (CollUtil.isEmpty(roleIds)) {
            return CollUtil.newArrayList();
        }

        // 根据角色Ids取BO
        List<SysRoleEntity> entityList = this.listByIds(roleIds);

        return this.entityList2BOs(entityList);
    }


    /*
    私有方法
    ------------------------------------------------------------------------------------------------
     */

    private SysRoleBO entity2BO(SysRoleEntity entity) {
        if (entity == null) {
            return null;
        }

        SysRoleBO bo = new SysRoleBO();
        BeanUtil.copyProperties(entity, bo);

        // 可以在此处为BO填充字段
        bo.setMenuIds(sysRoleMenuRelationService.listMenuIdByRoleIds(
           CollUtil.newArrayList(bo.getId())
        ));
        return bo;
    }

    private List<SysRoleBO> entityList2BOs(List<SysRoleEntity> entityList) {
        // 深拷贝
        List<SysRoleBO> ret = new ArrayList<>(entityList.size());
        entityList.forEach(
                entity -> ret.add(this.entity2BO(entity))
        );

        return ret;
    }

    private PageResult<SysRoleBO> entityPage2BOPage(Page<SysRoleEntity> entityPage) {
        PageResult<SysRoleBO> ret = new PageResult<>();
        BeanUtil.copyProperties(entityPage, ret);
        ret.setRecords(this.entityList2BOs(entityPage.getRecords()));

        return ret;
    }

    /**
     * 检查是否已存在同名数据
     * @param dto DTO
     */
    private void checkExist(AdminInsertOrUpdateSysRoleDTO dto) {
        SysRoleEntity existEntity = this.getOne(
                new QueryWrapper<SysRoleEntity>()
                        .lambda()
                        .eq(SysRoleEntity::getTitle, dto.getTitle())
                        .last(" LIMIT 1" )
        );

        if (existEntity != null && !existEntity.getId().equals(dto.getId())) {
            throw new BusinessException(400, "已存在相同后台角色，请重新输入");
        }
    }
}