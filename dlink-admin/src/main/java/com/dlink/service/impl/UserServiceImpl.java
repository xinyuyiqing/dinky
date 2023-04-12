/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.dlink.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dlink.assertion.Asserts;
import com.dlink.common.result.Result;
import com.dlink.context.TenantContextHolder;
import com.dlink.db.service.impl.SuperServiceImpl;
import com.dlink.dto.LoginUTO;
import com.dlink.dto.UserDTO;
import com.dlink.mapper.UserMapper;
import com.dlink.model.*;
import com.dlink.service.*;
import com.dlink.utils.JwtTokenUtils;
import com.dlink.utils.MessageResolverUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;


/**
 * UserServiceImpl
 *
 * @author wenmo
 * @since 2021/11/28 13:39
 */
@Service
public class UserServiceImpl extends SuperServiceImpl<UserMapper, User> implements UserService {

    private static final String DEFAULT_PASSWORD = "123456";

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private UserTenantService userTenantService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RoleSelectPermissionsService roleSelectPermissionsService;

    @Autowired
    private TenantServiceImpl tenantServiceImpl;

    @Override
    public Result registerUser(User user) {
        User userByUsername = getUserByUsername(user.getUsername());
        if (Asserts.isNotNull(userByUsername)) {
            return Result.failed(MessageResolverUtils.getMessage("user.register.account.exists"));
        }
        if (Asserts.isNullString(user.getPassword())) {
            user.setPassword(DEFAULT_PASSWORD);
        }
        user.setPassword(SaSecureUtil.md5(user.getPassword()));
        user.setEnabled(true);
        user.setIsDelete(false);
        if (save(user)) {
            return Result.succeed(MessageResolverUtils.getMessage("user.register.success"));
        } else {
            return Result.failed(MessageResolverUtils.getMessage("user.register.account.exists"));
        }
    }

    @Override
    public boolean modifyUser(User user) {
        if (Asserts.isNull(user.getId())) {
            return false;
        }
        return updateById(user);
    }

    @Override
    public Result modifyPassword(String username, String password, String newPassword) {
        User user = getUserByUsername(username);
        if (Asserts.isNull(user)) {
            return Result.failed(MessageResolverUtils.getMessage("login.user.not.exists"));
        }
        if (!Asserts.isEquals(SaSecureUtil.md5(password), user.getPassword())) {
            return Result.failed(MessageResolverUtils.getMessage("user.oldpassword.incorrect"));
        }
        user.setPassword(SaSecureUtil.md5(newPassword));
        if (updateById(user)) {
            return Result.succeed(MessageResolverUtils.getMessage("user.change.password.success"));
        }
        return Result.failed(MessageResolverUtils.getMessage("user.change.password.failed"));
    }

    @Override
    public boolean removeUser(Integer id) {
        User user = new User();
        user.setId(id);
        user.setIsDelete(true);
        return updateById(user);
    }

    @Override
    public Result loginUser(LoginUTO loginUTO) {
        User user = getUserByUsername(loginUTO.getUsername());
        if (Asserts.isNull(user)) {
            return Result.failed(MessageResolverUtils.getMessage("login.fail"));
        }
        String userPassword = user.getPassword();
        if (Asserts.isNullString(loginUTO.getPassword())) {
            return Result.failed(MessageResolverUtils.getMessage("login.password.notnull"));
        }
        if (Asserts.isEquals(SaSecureUtil.md5(loginUTO.getPassword()), userPassword)) {
            if (user.getIsDelete()) {
                return Result.failed(MessageResolverUtils.getMessage("login.user.not.exists"));
            }
            if (!user.getEnabled()) {
                return Result.failed(MessageResolverUtils.getMessage("login.user.disabled"));
            }

            // 将前端入参 租户id 放入上下文
            TenantContextHolder.set(loginUTO.getTenantId());

            // get user tenants and roles
            UserDTO userDTO = getUserALLBaseInfo(loginUTO, user);

            StpUtil.login(user.getId(), loginUTO.isAutoLogin());
            StpUtil.getSession().set("user", userDTO);
            return Result.succeed(userDTO, MessageResolverUtils.getMessage("login.success"));
        } else {
            return Result.failed(MessageResolverUtils.getMessage("login.fail"));
        }
    }

    //集成大数据平台的登录接口
    @Override
    public Result dcqcloginUser(JSONObject token) {
        String Username = JwtTokenUtils.getUsername(token.get("token").toString());
        String WORKSPACE_ID = JwtTokenUtils.getWORKSPACE_ID(token.get("token").toString());
        String WORKSPACE_NAME = JwtTokenUtils.getWORKSPACE_NAME(token.get("token").toString());
        User user = getUserByUsername(Username);

        //如果用户不存在则新建
        if (Asserts.isNull(user)) {
            User newuser = new User();
            newuser.setUsername(Username);
            registerUser(newuser);
            user = getUserByUsername(Username);
        }

        //新建或者修改工作空间
        Tenant tenant = new Tenant();
        tenant.setTenantCode(WORKSPACE_NAME);
        tenant.setNote(WORKSPACE_ID);
        tenantService.saveOrUpdateTenant(tenant);

        //获取空间已分配的用户
        Tenant tenantByTenantCode = tenantServiceImpl.getTenantByTenantCode(tenant.getTenantCode());
        int tenantId = tenantByTenantCode.getId();
        List<UserTenant> userTenants = userTenantService.getBaseMapper().selectList(new QueryWrapper<UserTenant>().eq("tenant_id", tenantId));
        List<Integer> userIds = new ArrayList<>();
        for (UserTenant userTenant : userTenants) {
            userIds.add(userTenant.getUserId());
        }

        //判断登陆用户在空间里没，不存在就加上
        if (userIds.contains(user.getId())) {

        } else {
            userIds.add(user.getId());
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode parentNode = objectMapper.createObjectNode();
            parentNode.put("tenantId", tenantId);
            parentNode.putPOJO("users", userIds);
            String json;
            JsonNode jsonNode;
            try {
                json = objectMapper.writeValueAsString(parentNode);
                jsonNode = objectMapper.readTree(json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            //分配用户到对应的工作空间
            tenantService.distributeUsers(jsonNode);
        }

        // 将前端入参 租户id 放入上下文
        TenantContextHolder.set(tenantId);
        LoginUTO loginUTO = new LoginUTO();
        loginUTO.setUsername(Username);
        loginUTO.setTenantId(tenantId);

        // get user tenants and roles
        UserDTO userDTO = getUserALLBaseInfo(loginUTO, user);
        StpUtil.login(user.getId(), loginUTO.isAutoLogin());
        StpUtil.getSession().set("user", userDTO);
        return Result.succeed(userDTO, MessageResolverUtils.getMessage("login.success"));
    }

    private UserDTO getUserALLBaseInfo(LoginUTO loginUTO, User user) {
        UserDTO userDTO = new UserDTO();
        List<Role> roleList = new LinkedList<>();
        List<Tenant> tenantList = new LinkedList<>();

        List<UserRole> userRoles = userRoleService.getUserRoleByUserId(user.getId());
        List<UserTenant> userTenants = userTenantService.getUserTenantByUserId(user.getId());

        Tenant currentTenant = tenantService.getBaseMapper().selectById(loginUTO.getTenantId());

        userRoles.forEach(userRole -> {
            Role role = roleService.getBaseMapper().selectById(userRole.getRoleId());
            if (Asserts.isNotNull(role)) {
                roleList.add(role);
            }
        });

        userTenants.forEach(userTenant -> {
            Tenant tenant = tenantService.getBaseMapper()
                    .selectOne(new QueryWrapper<Tenant>().eq("id", userTenant.getTenantId()));
            if (Asserts.isNotNull(tenant)) {
                tenantList.add(tenant);
            }
        });

        userDTO.setUser(user);
        userDTO.setRoleList(roleList);
        userDTO.setTenantList(tenantList);
        userDTO.setCurrentTenant(currentTenant);
        return userDTO;
    }

    @Override
    public User getUserByUsername(String username) {
        User user = getOne(new QueryWrapper<User>().eq("username", username));
        if (Asserts.isNotNull(user)) {
            user.setIsAdmin(Asserts.isEqualsIgnoreCase(username, "admin"));
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result grantRole(JsonNode para) {
        if (para.size() > 0) {
            List<UserRole> userRoleList = new ArrayList<>();
            Integer userId = para.get("userId").asInt();
            userRoleService.remove(new QueryWrapper<UserRole>().eq("user_id", userId));
            JsonNode userRoleJsonNode = para.get("roles");
            for (JsonNode ids : userRoleJsonNode) {
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(ids.asInt());
                userRoleList.add(userRole);
            }
            // save or update user role
            boolean result = userRoleService.saveOrUpdateBatch(userRoleList, 1000);
            if (result) {
                return Result.succeed("用户授权角色成功");
            } else {
                if (userRoleList.size() == 0) {
                    return Result.succeed("该用户绑定的角色已被全部删除");
                }
                return Result.failed("用户授权角色失败");
            }
        } else {
            return Result.failed("请选择要授权的角色");
        }
    }

    @Override
    public Result getTenants(String username) {
        User user = getUserByUsername(username);
        if (Asserts.isNull(user)) {
            return Result.failed("该账号不存在,获取租户失败");
        }

        List<UserTenant> userTenants = userTenantService.getUserTenantByUserId(user.getId());
        if (userTenants.size() == 0) {
            return Result.failed("用户未绑定租户,获取租户失败");
        }

        Set<Integer> tenantIds = new HashSet<>();
        userTenants.forEach(userTenant -> tenantIds.add(userTenant.getTenantId()));
        List<Tenant> tenants = tenantService.getTenantByIds(tenantIds);
        return Result.succeed(tenants, MessageResolverUtils.getMessage("response.get.success"));
    }

    @Override
    public List<Role> getCurrentRole() {
        if (StpUtil.isLogin()) {
            Integer userId = StpUtil.getLoginIdAsInt();
            if (Asserts.isNull(userId)) {
                return new ArrayList<>();
            }
            return roleService.getRoleByUserId(userId);
        }
        return new ArrayList<>();
    }

    @Override
    public List<RoleSelectPermissions> getCurrentRoleSelectPermissions() {
        List<Role> currentRole = getCurrentRole();
        if (Asserts.isNullCollection(currentRole)) {
            return new ArrayList<>();
        }
        List<Integer> roleIds = currentRole.stream().map(Role::getId).collect(Collectors.toList());
        return roleSelectPermissionsService.listRoleSelectPermissionsByRoleIds(roleIds);
    }

}
