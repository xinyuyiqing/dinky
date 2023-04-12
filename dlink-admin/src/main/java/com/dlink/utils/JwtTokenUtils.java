package com.dlink.utils;

import com.alibaba.fastjson.JSON;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by jingwk on 2019/12/01
 */
public class JwtTokenUtils {

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private static final String SECRET = "dcqc-bigdata";
    private static final String ISS = "admin";

    private static final String SPLIT_COMMA = ",";

    // 角色的key
    private static final String ROLE_CLAIMS = "rol";

    // 工作空间ID
    private static final String WORKSPACE_ID = "workspaceId";

    private static final String WORKSPACE_NAME = "workspaceName";

    // 过期时间是3600秒，既是24个小时
    private static final long EXPIRATION = 86400L;

    // 选择了记住我之后的过期时间为7天
    private static final long EXPIRATION_REMEMBER = 7 * EXPIRATION;

    // 创建token
    public static String createToken(Long id, String username, String role, boolean isRememberMe,String workspaceId,String workspaceName) {
        long expiration = isRememberMe ? EXPIRATION_REMEMBER : EXPIRATION;
        HashMap<String, Object> map = new HashMap<>();
        map.put(ROLE_CLAIMS, role);
        map.put(WORKSPACE_ID,workspaceId);
        map.put(WORKSPACE_NAME,workspaceName);
        return Jwts.builder()
                .signWith(SignatureAlgorithm.HS512, SECRET)
                .setClaims(map)
                .setIssuer(ISS)
                .setSubject(id + SPLIT_COMMA + username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .compact();
    }

    // 从token中获取用户名
    public static String getUsername(String token) {
        List<String> userInfo = Arrays.asList(getTokenBody(token).getSubject().split(SPLIT_COMMA));
        return userInfo.get(1);
    }

    // 从token中获取用户Id
    public static Long getUserId(String token) {
        String s= JSON.toJSONString(getTokenBody(token).getSubject());
        List<String> userInfo = Arrays.asList(getTokenBody(token).getSubject().split(SPLIT_COMMA));
        return Long.parseLong(userInfo.get(0));
    }

    // 获取用户角色
    public static String getUserRole(String token) {
        return (String) getTokenBody(token).get(ROLE_CLAIMS);
    }


    // 从token中获取WORKSPACE_ID
    public static String getWORKSPACE_ID(String token) {
        return (String) getTokenBody(token).get(WORKSPACE_ID);
    }


    public static String getWORKSPACE_NAME(String token) {
        return (String) getTokenBody(token).get(WORKSPACE_NAME);
    }

    // 是否已过期
    public static boolean isExpiration(String token) {
        try {
            return getTokenBody(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    private static Claims getTokenBody(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET)
                .parseClaimsJws(token)
                .getBody();
    }

}
